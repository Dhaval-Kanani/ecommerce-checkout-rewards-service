# Design decisions

Scope of this document: the concurrency, idempotency and inventory work. It
explains what is guaranteed, how, what was traded away, and what is still open.

## Invariants

These are the properties the service holds under any interleaving of requests.

1. Stock for an item is never negative.
2. A reservation across several items either decrements every line or none.
3. Units are conserved. Every unit is in stock or in a committed order, never
   lost and never duplicated.
4. A cart becomes at most one order.
5. An idempotency key places at most one order.
6. A coupon is redeemed at most once.
7. A checkout that does not reach its commit point leaves no trace: no order,
   no consumed coupon, no missing stock, and the cart is still there.

## Ambiguities and the semantics chosen

**Cart prices when the catalogue changes.** A cart line snapshots the price at
the moment the item was added, and checkout charges that snapshot. Chosen
because a customer should not see the total change under them between the cart
page and the confirmation. The consequence is that a long-lived cart can be
bought at a stale price. A production system would expire the snapshot, or
re-price and make the customer confirm. Order records keep their own copy of
the line, so a later price change never rewrites history.

**When stock is taken.** Adding to a cart reserves nothing. Only checkout does.
Chosen because holding stock for abandoned carts is worse than occasionally
telling a customer at checkout that an item just sold out. The cart-level check
is therefore advisory and can be stale by the time checkout runs, which is why
checkout re-checks under lock and is the authority.

**A retry of an in-flight checkout.** Rejected with 409 rather than blocked
until the first attempt resolves. Chosen because making one request wait on
another's lock turns a slow checkout into a pile-up of held connections. The
consequence is that a client retrying aggressively sees 409 and must back off.

**Milestone counting.** The coupon is minted on the order sequence number, which
is allocated after stock and coupon are secured. A checkout that fails after
that point leaves a gap in the sequence, so a milestone can in principle be
skipped. Accepted: the failure window is a few in-memory writes wide, and
counting committed orders instead would need its own serialisation.

## Material decisions

### 1. An inventory service that owns stock outright

- **Context.** Stock was a field on the item that nothing read or wrote.
- **Decision.** All stock access goes through `InventoryService`. Nothing else
  reads or writes `Item.stock`.
- **Alternatives.** Decrement inside the order service; or make the field an
  `AtomicInteger`.
- **Why.** Decrementing in the order service spreads the invariant across
  callers. An `AtomicInteger` makes a single decrement atomic but not a
  check-then-decrement across several items, which is the operation that
  actually matters here.
- **Consequence.** Reads outside the service can be stale. That is why the
  cart-level check is documented as advisory.

### 2. Per-item locks acquired in a total order

- **Context.** A multi-item cart must reserve all its lines or none.
- **Decision.** Lock every item in the cart, in ascending item-id order, then
  validate all lines, then decrement all lines.
- **Alternatives.** One global checkout lock; or optimistic retry on a version.
- **Why.** A global lock serialises unrelated checkouts and throws away the
  concurrency the assignment is about. Optimistic retry needs a bounded retry
  policy and starves under contention. Sorting the ids gives a total ordering
  over lock acquisition, which is what makes deadlock impossible rather than
  merely unlikely.
- **Consequence.** Two carts sharing a hot item serialise on it, which is
  correct and unavoidable. The ordering rule is load-bearing, so
  `multiItemCheckoutsDoNotDeadlock` exists to catch anyone removing the sort.

### 3. Claiming the cart with an atomic remove

- **Context.** Nothing stopped two concurrent checkouts of one cart from both
  pricing it and both reserving stock.
- **Decision.** Checkout claims the cart with `ConcurrentHashMap.remove`, which
  returns the previous value under the key's lock. Exactly one caller gets it.
- **Alternatives.** A per-cart lock; or a status flag on the cart.
- **Why.** Remove-and-return is already atomic, needs no extra lock, and makes
  the cart's disappearance and its claim the same event, so the two cannot drift
  apart.
- **Consequence.** A failed checkout must put the cart back, which the
  compensation path does. Losing that call would silently destroy carts, so the
  restore is asserted in `cartSurvivesFailedCheckout`.

### 4. Idempotency keys owned via `putIfAbsent`, with a request fingerprint

- **Context.** No idempotency at all. A retry after a timeout could not learn
  what happened.
- **Decision.** Checkout requires an `Idempotency-Key` header. Ownership is
  decided by `putIfAbsent`. The record stores a SHA-256 fingerprint of the cart
  and coupon the key first arrived with. A completed key replays its order; an
  in-flight key is rejected with 409; a key reused for a different request is
  rejected with 422; a key whose attempt failed is released.
- **Alternatives.** Derive the key from the cart id; or keep failed keys.
- **Why.** Deriving from the cart id cannot distinguish a retry from a
  deliberate second purchase, and breaks once carts are reusable. Keeping failed
  keys permanently blocks a legitimate retry of a transient failure.
- **Consequence.** Clients must generate and reuse a key per attempt. Releasing
  a failed key means a retry re-does the work, which is correct because the
  failed attempt committed nothing.

### 5. A coupon state machine instead of a boolean

- **Context.** Three separate defects. Validation read a `used` flag and
  checkout wrote it later, so two checkouts could both redeem. The flag was set
  before the order was saved, so a later failure burned the coupon. And a single
  `currentActiveCode` field, overwritten by each newly minted coupon, made every
  previously issued coupon permanently unredeemable.
- **Decision.** `ISSUED -> RESERVED -> REDEEMED`, with `RESERVED -> ISSUED` on
  failure. Every transition runs inside `ConcurrentHashMap.compute`, so
  check-and-set is indivisible. The active-code field is gone; any `ISSUED`
  coupon is redeemable.
- **Alternatives.** Synchronise the order service's coupon block; or a lock per
  coupon.
- **Why.** Putting the transition in the store means no caller can bypass it.
  `compute` already provides the per-key mutual exclusion a dedicated lock would.
- **Consequence.** A coupon left in `RESERVED` would indicate a leaked claim,
  which is why the admin report exposes that count.

### 6. Commit point and compensation

- **Decision.** Resources are acquired in a fixed order: cart, then stock, then
  coupon. The commit point is the promotion of the coupon to `REDEEMED` after
  the order is saved. Anything that throws before it releases all four in
  reverse: delete the saved order, release the coupon, restore the stock, put
  the cart back.
- **Why.** A single ordering makes the rollback mechanical rather than
  case-by-case.
- **Consequence.** Rollback is guarded by flags set only after each acquisition
  succeeds. An earlier version keyed the stock rollback off the line map being
  non-null, which would have restored units that were never taken and invented
  inventory. Order deletion was added after a mutation test showed a failed
  checkout could leave an order behind.

## Error model

| Condition | Status | Type |
|---|---|---|
| Unknown cart, item, or a cart already checked out | 404 | `ResourceNotFoundException` |
| Empty cart, unknown coupon, bad quantity, missing key | 400 | `EmptyCartException`, `InvalidDiscountCodeException`, `IllegalArgumentException` |
| Not enough stock | 409 | `InsufficientStockException` |
| Coupon redeemed or held by another checkout | 409 | `CouponUnavailableException` |
| Attempt for this key still running | 409 | `CheckoutInProgressException` |
| Key reused for a different request | 422 | `IdempotencyKeyConflictException` |

The split matters: 400 means the request is wrong and retrying will not help,
409 means it was well-formed but lost a race and may be worth retrying, 422
means the client has a bug in how it generates keys.

## Testing approach

Service tests use real repositories. The originals mocked them, which meant no
test could observe stock, atomicity or any repository-level guarantee.

`CheckoutConcurrencyTest` covers the six contended cases. Where the resource is
a single object exactly one caller may claim, the race repeats over forty rounds
with racers aligned on a `CyclicBarrier`, because one round can miss a narrow
window and pass for the wrong reason.

Each concurrency test was validated by reintroducing the defect it targets and
confirming the test fails: unsorted lock acquisition, an unlocked stock
decrement, a read-check-write coupon claim, get-then-put on the idempotency key,
and get-then-remove on the cart claim. Two tests passed against their mutation
on the first attempt and were rewritten until they did not. That exercise is
also what surfaced the phantom-order bug in decision 6.

## Deliberately not done

- **Money is still `BigDecimal`.** The arithmetic is exact, but the values
  serialise as JSON numbers, so a discount of `100.00` goes out on the wire as
  `100.0` and scale is lost at the boundary. Integer minor units would avoid
  both that and any float handling on the client. Out of scope for this change,
  and the first thing worth doing next.
- **No persistence.** Everything is in memory.
- **No idempotency record expiry.** The map grows without bound.
- **No authentication** on admin endpoints.
- **No admin-triggered coupon generation.** Coupons are only minted at
  milestones.

## Scaling to more than one instance

Every guarantee here rests on in-process primitives, so none of it survives a
second instance. The structure is deliberately shaped so the primitives are the
only thing that has to change:

- Stock reservation becomes a conditional update in one statement, along the
  lines of an update that decrements where the remaining stock is sufficient,
  with the affected row count deciding success. Multi-line carts either use a
  transaction with rows locked in item-id order, keeping the same total
  ordering, or a single statement per line with rollback.
- The idempotency record becomes a table with the key as primary key. The
  insert replaces `putIfAbsent`: whoever inserts first owns the attempt.
- The coupon transition becomes a conditional update from `ISSUED` to
  `RESERVED`, with the affected row count deciding the winner.
- Cart claim becomes a conditional delete or a status flip, again decided by
  rows affected.
- Compensation folds into the database transaction, and most of the explicit
  rollback goes away.

The lock ordering, the commit point and the state machines carry over unchanged.
What changes is where mutual exclusion comes from.
