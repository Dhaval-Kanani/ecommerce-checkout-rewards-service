# Ecommerce Checkout and Rewards Service

A Spring Boot checkout service for carts, inventory, orders and milestone
coupons. It runs entirely in memory with no external dependencies.

The interesting part of this service is not the CRUD. It is what happens when
requests overlap: not overselling, not charging twice for a retry, and not
letting two checkouts spend one coupon. Those guarantees, and the tests that
hold them up, are described in [DECISIONS.md](DECISIONS.md).

## Running it

Requires Java 17 and Maven 3.6+.

```bash
mvn spring-boot:run
```

Starts on `http://localhost:8080` with five seeded products.

```bash
mvn test
```

Runs the full suite, including the concurrency tests.

## Guarantees

| Guarantee | How it holds |
|---|---|
| Stock never goes negative | Every reservation validates and decrements under per-item locks |
| A multi-item cart reserves all lines or none | All items are locked before any is checked |
| Concurrent reservations never deadlock | Locks are always taken in ascending item-id order |
| One cart produces at most one order | Checkout claims the cart with an atomic remove |
| A retried checkout charges once | Ownership of the idempotency key is decided by `putIfAbsent` |
| A coupon is redeemed at most once | Status transitions are compare-and-set inside the map's per-key lock |
| A failed checkout consumes nothing | Order, coupon, stock and cart are all rolled back |

## API

### Items

```http
GET /api/items
```

Returns the catalogue with current stock.

### Cart

```http
POST /api/cart/items?cartId={optional}
Content-Type: application/json

{ "itemId": "ITEM001", "quantity": 2 }
```

Creates a cart when `cartId` is omitted. Rejects a quantity above available
stock, though it reserves nothing. Checkout is the authoritative check.

```http
GET    /api/cart/{cartId}
DELETE /api/cart/{cartId}/items/{itemId}
```

### Checkout

```http
POST /api/checkout
Content-Type: application/json
Idempotency-Key: 7f3a1c2e-...

{ "cartId": "...", "discountCode": "DISCOUNT10-AB12CD34" }
```

`Idempotency-Key` is required. Send a fresh key per checkout attempt and reuse
that same key when retrying it.

| Status | Meaning |
|---|---|
| 201 | This request placed the order |
| 200 | This key already placed an order; the same order is returned |
| 409 | An attempt for this key is still running, or the coupon is gone, or stock ran out |
| 422 | This key was first used for a different cart or coupon |
| 400 | Missing key, empty cart, or an unknown coupon |
| 404 | Cart not found, or already checked out |

### Admin

```http
GET /api/admin/stats
GET /api/admin/discount-codes
```

`stats` reports gross revenue, total discount, net revenue, how many orders
redeemed a coupon, and coupon counts by lifecycle stage. A non-zero `reserved`
count while the system is idle means a checkout leaked a claim.

## Configuration

`src/main/resources/application.properties`

```properties
server.port=8080
app.discount.nth-order=3
```

Every nth completed order mints a 10% coupon. Any unredeemed coupon stays
redeemable; minting a new one does not retire the old ones.

## Layout

```
src/main/java/com/ecommerce/
  controller/   REST endpoints
  service/      InventoryService, CartService, OrderService, AdminService
  repository/   In-memory stores; the atomic primitives live here
  model/        Domain types and the coupon and idempotency state machines
  dto/          Request and response shapes
  exception/    Error types and the global handler
src/test/java/com/ecommerce/service/
  CartServiceTest, OrderServiceTest, AdminServiceTest
  CheckoutConcurrencyTest    the invariants under contention
```

## Known limits

- In-memory only. Everything is lost on restart.
- Money is `BigDecimal`, not integer minor units.
- No authentication. Admin endpoints are open.
- Single instance. See the scaling notes in [DECISIONS.md](DECISIONS.md).
