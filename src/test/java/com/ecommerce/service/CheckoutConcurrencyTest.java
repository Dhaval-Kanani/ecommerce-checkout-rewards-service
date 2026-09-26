package com.ecommerce.service;

import com.ecommerce.dto.CheckoutResult;
import com.ecommerce.exception.CheckoutInProgressException;
import com.ecommerce.exception.CouponUnavailableException;
import com.ecommerce.exception.InsufficientStockException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.CouponStatus;
import com.ecommerce.model.DiscountCode;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.DiscountCodeRepository;
import com.ecommerce.repository.IdempotencyRepository;
import com.ecommerce.repository.ItemRepository;
import com.ecommerce.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The invariants that only break under contention.
 *
 * <p>Two shapes of test are used. Where the contended resource is plural, such as
 * stock, one large burst is enough. Where it is a single object that exactly one
 * caller may claim, such as a cart, a coupon or an idempotency key, the race is
 * repeated over many rounds with the racers aligned on a {@link CyclicBarrier}.
 * A single round can miss a narrow window and pass for the wrong reason; many
 * aligned rounds will not.
 *
 * <p>Every test is time-bounded, so a lock-ordering regression shows up as a
 * failure rather than a suite that hangs.
 */
class CheckoutConcurrencyTest {

    private static final String LAPTOP = "ITEM001";     // seeded stock 50
    private static final String MOUSE = "ITEM002";      // seeded stock 100
    private static final String MONITOR = "ITEM004";    // seeded stock 30
    private static final String HEADPHONES = "ITEM005"; // seeded stock 60
    private static final int LAPTOP_STOCK = 50;
    private static final int MOUSE_STOCK = 100;
    private static final int MONITOR_STOCK = 30;
    private static final int HEADPHONE_STOCK = 60;
    private static final int NTH_ORDER = 3;

    private CartService cartService;
    private OrderService orderService;
    private InventoryService inventoryService;
    private OrderRepository orderRepository;
    private DiscountCodeRepository discountCodeRepository;

    @BeforeEach
    void setUp() {
        ItemRepository itemRepository = new ItemRepository();
        CartRepository cartRepository = new CartRepository();
        inventoryService = new InventoryService(itemRepository);
        cartService = new CartService(cartRepository, itemRepository, inventoryService);
        orderRepository = new OrderRepository();
        discountCodeRepository = new DiscountCodeRepository();
        orderService = new OrderService(orderRepository, cartRepository, discountCodeRepository,
                new IdempotencyRepository(), cartService, inventoryService);
        ReflectionTestUtils.setField(orderService, "nthOrder", NTH_ORDER);
    }

    @Test
    @Timeout(60)
    @DisplayName("twice as many buyers as units sells exactly the units that exist")
    void concurrentCheckoutsNeverOversell() throws Exception {
        int buyers = MONITOR_STOCK * 2;
        // Carts are filled up front, while stock is still ample, so the contention
        // under test is at checkout rather than at add-to-cart.
        List<String> carts = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            carts.add(cartWith(MONITOR, 1));
        }

        List<Outcome> outcomes = burst(carts.stream().map(id -> checkoutTask(id, null)).toList());

        assertEquals(MONITOR_STOCK, successes(outcomes), "sold a different number of units than existed");
        assertEquals(MONITOR_STOCK, failures(outcomes, InsufficientStockException.class));
        assertEquals(0, inventoryService.availableStock(MONITOR));
        assertEquals(MONITOR_STOCK, orderRepository.findAll().size());
        assertEquals(MONITOR_STOCK, unitsSold(MONITOR));
    }

    @Test
    @Timeout(60)
    @DisplayName("concurrent checkouts of one cart produce exactly one order, every round")
    void oneCartYieldsOneOrder() throws Exception {
        int rounds = 40;
        int racers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        try {
            for (int round = 0; round < rounds; round++) {
                String cartId = cartWith(LAPTOP, 1);

                List<Outcome> outcomes = race(pool, racers, i -> checkoutTask(cartId, null));

                assertEquals(1, successes(outcomes), "round " + round + " produced two orders from one cart");
                assertEquals(racers - 1, failures(outcomes, ResourceNotFoundException.class),
                        "round " + round + " failed for an unexpected reason");
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(rounds, orderRepository.findAll().size());
        assertEquals(LAPTOP_STOCK - rounds, inventoryService.availableStock(LAPTOP),
                "a cart was priced and reserved more than once");
    }

    @Test
    @Timeout(60)
    @DisplayName("concurrent retries sharing one idempotency key produce exactly one order, every round")
    void oneIdempotencyKeyYieldsOneOrder() throws Exception {
        int rounds = 40;
        int racers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        try {
            for (int round = 0; round < rounds; round++) {
                String cartId = cartWith(LAPTOP, 1);
                String sharedKey = UUID.randomUUID().toString();

                List<Outcome> outcomes = race(pool, racers,
                        i -> () -> orderService.checkout(cartId, null, sharedKey));

                long placed = outcomes.stream()
                        .filter(o -> o.succeeded() && !o.result.isReplayed())
                        .count();
                assertEquals(1, placed, "round " + round + ": more than one attempt placed an order");

                // Anything that did not place the order either replayed it or was
                // told the first attempt was still running. Nothing else is correct.
                long accounted = outcomes.stream()
                        .filter(o -> (o.succeeded() && o.result.isReplayed())
                                || o.error instanceof CheckoutInProgressException)
                        .count();
                assertEquals(racers - 1, accounted, "round " + round + ": unexpected outcome");

                outcomes.stream().filter(Outcome::succeeded).forEach(o ->
                        assertEquals(1, o.result.getOrder().getItems().size()));
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(rounds, orderRepository.findAll().size(), "a shared key placed more than one order");
        assertEquals(LAPTOP_STOCK - rounds, inventoryService.availableStock(LAPTOP));
    }

    @Test
    @Timeout(60)
    @DisplayName("one coupon is redeemed exactly once, every round")
    void couponIsRedeemedExactlyOnce() throws Exception {
        int rounds = 40;
        int racers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        try {
            for (int round = 0; round < rounds; round++) {
                // Seeded straight into the store, so this test isolates the redemption
                // race from milestone minting.
                String code = "RACE-" + round;
                discountCodeRepository.save(new DiscountCode(code, round, new BigDecimal("10")));

                List<String> carts = new ArrayList<>();
                for (int i = 0; i < racers; i++) {
                    carts.add(cartWith(MOUSE, 1));
                }

                List<Outcome> outcomes = race(pool, racers, i -> checkoutTask(carts.get(i), code));

                assertEquals(1, successes(outcomes), "round " + round + ": coupon was shared");
                assertEquals(racers - 1, failures(outcomes, CouponUnavailableException.class),
                        "round " + round + ": unexpected failure");
                assertEquals(CouponStatus.REDEEMED,
                        discountCodeRepository.findByCode(code).orElseThrow().getStatus());
            }
        } finally {
            pool.shutdownNow();
        }

        // One order per round and not one more. A coupon claimed twice would leave a
        // second, rolled-back order behind.
        assertEquals(rounds, orderRepository.findAll().size());
        assertEquals(rounds, discountCodeRepository.countByStatus(CouponStatus.REDEEMED));
        assertEquals(0, discountCodeRepository.countByStatus(CouponStatus.RESERVED),
                "a coupon was left stuck in RESERVED");
        assertEquals(MOUSE_STOCK - rounds, inventoryService.availableStock(MOUSE),
                "stock was not returned to the checkouts that lost the coupon");
    }

    @Test
    @Timeout(60)
    @DisplayName("multi-item carts locking in opposite orders do not deadlock")
    void multiItemCheckoutsDoNotDeadlock() throws Exception {
        int pairs = 20;
        List<Callable<CheckoutResult>> tasks = new ArrayList<>();
        for (int i = 0; i < pairs; i++) {
            // One cart lists the laptop first, the other lists it last. Reserving in
            // cart order would let these two hold each other's item lock.
            String ascending = cartWith(LAPTOP, 1);
            cartService.addItemToCart(ascending, HEADPHONES, 1);
            String descending = cartWith(HEADPHONES, 1);
            cartService.addItemToCart(descending, LAPTOP, 1);
            tasks.add(checkoutTask(ascending, null));
            tasks.add(checkoutTask(descending, null));
        }

        List<Outcome> outcomes = burst(tasks);

        assertEquals(pairs * 2, successes(outcomes));
        assertEquals(LAPTOP_STOCK - pairs * 2, inventoryService.availableStock(LAPTOP));
        assertEquals(HEADPHONE_STOCK - pairs * 2, inventoryService.availableStock(HEADPHONES));
    }

    @Test
    @Timeout(60)
    @DisplayName("stock is conserved when successes and failures are interleaved")
    void stockIsConservedAcrossMixedOutcomes() throws Exception {
        int each = 20;
        List<Callable<CheckoutResult>> tasks = new ArrayList<>();
        for (int i = 0; i < each; i++) {
            tasks.add(checkoutTask(cartWith(MOUSE, 2), null));
            // These fail at coupon reservation, after stock has been reserved.
            tasks.add(checkoutTask(cartWith(MOUSE, 2), "NOT-A-REAL-CODE"));
        }

        List<Outcome> outcomes = burst(tasks);

        assertEquals(each, successes(outcomes));
        int sold = unitsSold(MOUSE);
        assertEquals(each * 2, sold);
        assertEquals(MOUSE_STOCK, sold + inventoryService.availableStock(MOUSE),
                "units were lost or invented");
    }

    // --- helpers ---------------------------------------------------------------

    private String cartWith(String itemId, int quantity) {
        return cartService.addItemToCart(null, itemId, quantity).getCartId();
    }

    private Callable<CheckoutResult> checkoutTask(String cartId, String discountCode) {
        String key = UUID.randomUUID().toString();
        return () -> orderService.checkout(cartId, discountCode, key);
    }

    private int unitsSold(String itemId) {
        return orderRepository.findAll().stream()
                .flatMap(order -> order.getItems().stream())
                .filter(line -> line.getItemId().equals(itemId))
                .mapToInt(line -> line.getQuantity())
                .sum();
    }

    /**
     * Runs one round with every racer aligned on a barrier, so all of them enter
     * checkout at the same instant. The pool must have at least {@code racers}
     * threads or the barrier cannot be satisfied.
     */
    private List<Outcome> race(ExecutorService pool, int racers,
                               IntFunction<Callable<CheckoutResult>> factory) throws Exception {
        CyclicBarrier gate = new CyclicBarrier(racers);
        List<Future<CheckoutResult>> futures = new ArrayList<>(racers);
        for (int i = 0; i < racers; i++) {
            Callable<CheckoutResult> task = factory.apply(i);
            futures.add(pool.submit(() -> {
                gate.await(20, TimeUnit.SECONDS);
                return task.call();
            }));
        }
        return collect(futures);
    }

    /** Fires a large batch at once from a held start gate. */
    private List<Outcome> burst(List<Callable<CheckoutResult>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(tasks.size(), 64));
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<CheckoutResult>> futures = new ArrayList<>(tasks.size());
        try {
            for (Callable<CheckoutResult> task : tasks) {
                futures.add(pool.submit(() -> {
                    gate.await();
                    return task.call();
                }));
            }
            gate.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(45, TimeUnit.SECONDS),
                    "checkouts did not finish in time, which suggests a deadlock");
        } finally {
            pool.shutdownNow();
        }
        return collect(futures);
    }

    private List<Outcome> collect(List<Future<CheckoutResult>> futures) throws Exception {
        List<Outcome> outcomes = new ArrayList<>(futures.size());
        for (Future<CheckoutResult> future : futures) {
            try {
                outcomes.add(new Outcome(future.get(30, TimeUnit.SECONDS), null));
            } catch (ExecutionException e) {
                outcomes.add(new Outcome(null, e.getCause()));
            }
        }
        return outcomes;
    }

    private long successes(List<Outcome> outcomes) {
        return outcomes.stream().filter(Outcome::succeeded).count();
    }

    private long failures(List<Outcome> outcomes, Class<? extends Throwable> type) {
        return outcomes.stream().filter(o -> !o.succeeded() && type.isInstance(o.error)).count();
    }

    private record Outcome(CheckoutResult result, Throwable error) {
        boolean succeeded() {
            return result != null;
        }
    }
}
