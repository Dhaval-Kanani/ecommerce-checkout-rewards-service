package com.ecommerce.service;

import com.ecommerce.exception.EmptyCartException;
import com.ecommerce.exception.InsufficientStockException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.exception.CheckoutInProgressException;
import com.ecommerce.exception.CouponUnavailableException;
import com.ecommerce.exception.IdempotencyKeyConflictException;
import com.ecommerce.exception.InvalidDiscountCodeException;
import com.ecommerce.dto.CheckoutResult;
import com.ecommerce.model.Cart;
import com.ecommerce.model.CouponStatus;
import com.ecommerce.model.DiscountCode;
import com.ecommerce.model.Order;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.DiscountCodeRepository;
import com.ecommerce.repository.ItemRepository;
import com.ecommerce.repository.IdempotencyRepository;
import com.ecommerce.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {
    private static final String LAPTOP = "ITEM001";
    private static final String MOUSE = "ITEM002";
    private static final int LAPTOP_STOCK = 50;
    private static final int NTH_ORDER = 3;

    private CartService cartService;
    private OrderService orderService;
    private InventoryService inventoryService;
    private DiscountCodeRepository discountCodeRepository;
    private CartRepository cartRepository;
    private OrderRepository orderRepository;
    private IdempotencyRepository idempotencyRepository;

    @BeforeEach
    void setUp() {
        ItemRepository itemRepository = new ItemRepository();
        cartRepository = new CartRepository();
        inventoryService = new InventoryService(itemRepository);
        cartService = new CartService(cartRepository, itemRepository, inventoryService);
        discountCodeRepository = new DiscountCodeRepository();
        orderRepository = new OrderRepository();
        idempotencyRepository = new IdempotencyRepository();
        orderService = new OrderService(orderRepository, cartRepository, discountCodeRepository,
                idempotencyRepository, cartService, inventoryService);
        ReflectionTestUtils.setField(orderService, "nthOrder", NTH_ORDER);
    }

    private String cartWith(String itemId, int quantity) {
        return cartService.addItemToCart(null, itemId, quantity).getCartId();
    }

    private String newKey() {
        return UUID.randomUUID().toString();
    }

    private Order checkout(String cartId, String discountCode) {
        return checkout(orderService, cartId, discountCode);
    }

    private Order checkout(OrderService service, String cartId, String discountCode) {
        return service.checkout(cartId, discountCode, newKey()).getOrder();
    }

    @Test
    @DisplayName("checkout prices the cart and returns an order")
    void checkoutCreatesOrder() {
        Order order = checkout(cartWith(LAPTOP, 2), null);

        assertNotNull(order.getOrderId());
        assertEquals(0, new BigDecimal("1999.98").compareTo(order.getSubtotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getDiscountAmount()));
        assertEquals(0, new BigDecimal("1999.98").compareTo(order.getTotalAmount()));
    }

    @Test
    @DisplayName("checkout consumes stock for every line")
    void checkoutDecrementsStock() {
        checkout(cartWith(LAPTOP, 4), null);

        assertEquals(LAPTOP_STOCK - 4, inventoryService.availableStock(LAPTOP));
    }

    @Test
    @DisplayName("checkout consumes the cart")
    void checkoutRemovesCart() {
        String cartId = cartWith(LAPTOP, 1);

        checkout(cartId, null);

        assertThrows(RuntimeException.class, () -> cartService.getCart(cartId));
    }

    @Test
    @DisplayName("an empty cart cannot be checked out")
    void checkoutRejectsEmptyCart() {
        Cart empty = cartService.createCart();

        assertThrows(EmptyCartException.class, () -> checkout(empty.getCartId(), null));
    }

    @Test
    @DisplayName("checkout fails when stock ran out after the cart was filled")
    void checkoutRejectsCartBeyondStock() {
        String cartId = cartWith(LAPTOP, 5);

        inventoryService.reserve(Map.of(LAPTOP, LAPTOP_STOCK - 2));

        assertThrows(InsufficientStockException.class, () -> checkout(cartId, null));
        assertEquals(2, inventoryService.availableStock(LAPTOP));
        assertNotNull(cartService.getCart(cartId));
    }

    @Test
    @DisplayName("a checkout that fails after reserving gives the stock back")
    void failedCheckoutRestoresStock() {
        String cartId = cartWith(LAPTOP, 3);

        assertThrows(InvalidDiscountCodeException.class,
                () -> checkout(cartId, "NOT-A-REAL-CODE"));

        assertEquals(LAPTOP_STOCK, inventoryService.availableStock(LAPTOP));
    }

    @Test
    @DisplayName("every nth order mints a coupon")
    void nthOrderGeneratesCoupon() {
        for (int i = 0; i < NTH_ORDER - 1; i++) {
            checkout(cartWith(MOUSE, 1), null);
        }
        assertTrue(discountCodeRepository.findAll().isEmpty());

        checkout(cartWith(MOUSE, 1), null);

        assertEquals(1, discountCodeRepository.findAll().size());
    }

    @Test
    @DisplayName("a minted coupon takes ten percent off")
    void couponAppliesDiscount() {
        for (int i = 0; i < NTH_ORDER; i++) {
            checkout(cartWith(MOUSE, 1), null);
        }
        String code = discountCodeRepository.findAll().get(0).getCode();

        Order order = checkout(cartWith(LAPTOP, 1), code);

        assertEquals(0, new BigDecimal("999.99").compareTo(order.getSubtotal()));
        assertEquals(0, new BigDecimal("100.00").compareTo(order.getDiscountAmount()));
        assertEquals(0, new BigDecimal("899.99").compareTo(order.getTotalAmount()));
        assertEquals(code, order.getDiscountCode());
    }

    @Test
    @DisplayName("an unknown coupon is rejected")
    void unknownCouponRejected() {
        assertThrows(InvalidDiscountCodeException.class,
                () -> checkout(cartWith(LAPTOP, 1), "MADE-UP"));
    }

    @Test
    @DisplayName("a coupon cannot be redeemed twice")
    void couponCannotBeReusedSequentially() {
        for (int i = 0; i < NTH_ORDER; i++) {
            checkout(cartWith(MOUSE, 1), null);
        }
        DiscountCode coupon = discountCodeRepository.findAll().get(0);
        checkout(cartWith(LAPTOP, 1), coupon.getCode());

        String secondCart = cartWith(LAPTOP, 1);
        assertThrows(CouponUnavailableException.class,
                () -> checkout(secondCart, coupon.getCode()));
    }

    @Test
    @DisplayName("a redeemed coupon reaches its terminal status")
    void redeemedCouponIsTerminal() {
        for (int i = 0; i < NTH_ORDER; i++) {
            checkout(cartWith(MOUSE, 1), null);
        }
        DiscountCode coupon = discountCodeRepository.findAll().get(0);
        assertEquals(CouponStatus.ISSUED, coupon.getStatus());

        checkout(cartWith(LAPTOP, 1), coupon.getCode());

        assertEquals(CouponStatus.REDEEMED, coupon.getStatus());
        assertNotNull(coupon.getRedeemedAt());
    }

    @Test
    @DisplayName("minting a newer coupon does not strand an older unused one")
    void olderCouponStaysRedeemableAfterNewerIsMinted() {
        for (int i = 0; i < NTH_ORDER * 2; i++) {
            checkout(cartWith(MOUSE, 1), null);
        }
        assertEquals(2, discountCodeRepository.findAll().size());
        String oldestCode = discountCodeRepository.findAll().get(0).getCode();

        Order order = checkout(cartWith(LAPTOP, 1), oldestCode);

        assertEquals(oldestCode, order.getDiscountCode());
        assertEquals(0, new BigDecimal("100.00").compareTo(order.getDiscountAmount()));
    }

    @Test
    @DisplayName("a checkout that fails after claiming a coupon releases both coupon and stock")
    void failureAfterCouponClaimReleasesEverything() {
        for (int i = 0; i < NTH_ORDER; i++) {
            checkout(cartWith(MOUSE, 1), null);
        }
        String code = discountCodeRepository.findAll().get(0).getCode();
        int stockBefore = inventoryService.availableStock(LAPTOP);

        OrderService failing = new OrderService(explodingOrderRepository(), cartRepository,
                discountCodeRepository, idempotencyRepository, cartService, inventoryService);
        ReflectionTestUtils.setField(failing, "nthOrder", NTH_ORDER);

        assertThrows(IllegalStateException.class,
                () -> checkout(failing, cartWith(LAPTOP, 3), code));

        assertEquals(stockBefore, inventoryService.availableStock(LAPTOP));
        assertEquals(CouponStatus.ISSUED,
                discountCodeRepository.findByCode(code).orElseThrow().getStatus());

        Order retried = checkout(cartWith(LAPTOP, 1), code);
        assertEquals(code, retried.getDiscountCode());
    }

    @Test
    @DisplayName("a cart is handed back when checkout fails, so it can be retried")
    void cartSurvivesFailedCheckout() {
        String cartId = cartWith(LAPTOP, 3);

        assertThrows(InvalidDiscountCodeException.class,
                () -> checkout(cartId, "NOT-A-REAL-CODE"));

        Cart recovered = cartService.getCart(cartId);
        assertEquals(1, recovered.getItems().size());
        assertEquals(3, recovered.getItems().get(0).getQuantity());

        assertNotNull(checkout(cartId, null).getOrderId());
    }

    @Test
    @DisplayName("one cart cannot be checked out twice")
    void sameCartCannotBeCheckedOutTwice() {
        String cartId = cartWith(LAPTOP, 2);
        checkout(cartId, null);

        assertThrows(ResourceNotFoundException.class, () -> checkout(cartId, null));
        assertEquals(LAPTOP_STOCK - 2, inventoryService.availableStock(LAPTOP));
    }

    @Test
    @DisplayName("retrying with the same key returns the first order rather than placing a second")
    void retryWithSameKeyReplaysOrder() {
        String cartId = cartWith(LAPTOP, 2);
        String key = newKey();

        CheckoutResult first = orderService.checkout(cartId, null, key);
        CheckoutResult retry = orderService.checkout(cartId, null, key);

        assertFalse(first.isReplayed());
        assertTrue(retry.isReplayed());
        assertEquals(first.getOrder().getOrderId(), retry.getOrder().getOrderId());
        assertEquals(1, orderRepository.findAll().size());
        assertEquals(LAPTOP_STOCK - 2, inventoryService.availableStock(LAPTOP));
    }

    @Test
    @DisplayName("a replayed checkout does not redeem the coupon twice")
    void replayDoesNotDoubleRedeemCoupon() {
        for (int i = 0; i < NTH_ORDER; i++) {
            checkout(cartWith(MOUSE, 1), null);
        }
        String code = discountCodeRepository.findAll().get(0).getCode();
        String cartId = cartWith(LAPTOP, 1);
        String key = newKey();

        orderService.checkout(cartId, code, key);
        CheckoutResult retry = orderService.checkout(cartId, code, key);

        assertTrue(retry.isReplayed());
        assertEquals(1, discountCodeRepository.countByStatus(CouponStatus.REDEEMED));
    }

    @Test
    @DisplayName("reusing a key for a different request is rejected, not silently replayed")
    void keyReuseWithDifferentRequestIsRejected() {
        String firstCart = cartWith(LAPTOP, 1);
        String secondCart = cartWith(MOUSE, 1);
        String key = newKey();
        orderService.checkout(firstCart, null, key);

        assertThrows(IdempotencyKeyConflictException.class,
                () -> orderService.checkout(secondCart, null, key));
    }

    @Test
    @DisplayName("a key whose attempt failed can be retried with that same key")
    void keyIsReusableAfterAFailedAttempt() {
        String cartId = cartWith(LAPTOP, 3);
        String key = newKey();

        assertThrows(InvalidDiscountCodeException.class,
                () -> orderService.checkout(cartId, "NOT-A-REAL-CODE", key));

        assertEquals(0, idempotencyRepository.size());
        CheckoutResult retry = orderService.checkout(cartId, null, key);
        assertFalse(retry.isReplayed());
        assertEquals(LAPTOP_STOCK - 3, inventoryService.availableStock(LAPTOP));
    }

    @Test
    @DisplayName("a key held by an in-flight attempt is rejected")
    void inFlightKeyIsRejected() {
        String cartId = cartWith(LAPTOP, 1);
        String key = newKey();

        idempotencyRepository.begin(key, fingerprintOf(cartId, null));

        assertThrows(CheckoutInProgressException.class,
                () -> orderService.checkout(cartId, null, key));
    }

    @Test
    @DisplayName("a blank idempotency key is rejected")
    void blankKeyRejected() {
        String cartId = cartWith(LAPTOP, 1);

        assertThrows(IllegalArgumentException.class, () -> orderService.checkout(cartId, null, "  "));
        assertThrows(IllegalArgumentException.class, () -> orderService.checkout(cartId, null, null));
    }

    private String fingerprintOf(String cartId, String discountCode) {
        return (String) ReflectionTestUtils.invokeMethod(orderService, "fingerprint", cartId, discountCode);
    }

    private OrderRepository explodingOrderRepository() {
        return new OrderRepository() {
            @Override
            public Order save(Order order) {
                throw new IllegalStateException("order store unavailable");
            }
        };
    }
}
