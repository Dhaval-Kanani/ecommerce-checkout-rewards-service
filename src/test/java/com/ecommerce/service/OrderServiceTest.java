package com.ecommerce.service;

import com.ecommerce.exception.EmptyCartException;
import com.ecommerce.exception.InsufficientStockException;
import com.ecommerce.exception.InvalidDiscountCodeException;
import com.ecommerce.model.Cart;
import com.ecommerce.model.DiscountCode;
import com.ecommerce.model.Order;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.DiscountCodeRepository;
import com.ecommerce.repository.ItemRepository;
import com.ecommerce.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Checkout behaviour against real repositories, including stock effects. */
class OrderServiceTest {

    private static final String LAPTOP = "ITEM001";
    private static final String MOUSE = "ITEM002";
    private static final int LAPTOP_STOCK = 50;
    private static final int NTH_ORDER = 3;

    private CartService cartService;
    private OrderService orderService;
    private InventoryService inventoryService;
    private DiscountCodeRepository discountCodeRepository;

    @BeforeEach
    void setUp() {
        ItemRepository itemRepository = new ItemRepository();
        CartRepository cartRepository = new CartRepository();
        inventoryService = new InventoryService(itemRepository);
        cartService = new CartService(cartRepository, itemRepository, inventoryService);
        discountCodeRepository = new DiscountCodeRepository();
        orderService = new OrderService(new OrderRepository(), cartRepository,
                discountCodeRepository, cartService, inventoryService);
        ReflectionTestUtils.setField(orderService, "nthOrder", NTH_ORDER);
    }

    private String cartWith(String itemId, int quantity) {
        return cartService.addItemToCart(null, itemId, quantity).getCartId();
    }

    @Test
    @DisplayName("checkout prices the cart and returns an order")
    void checkoutCreatesOrder() {
        Order order = orderService.checkout(cartWith(LAPTOP, 2), null);

        assertNotNull(order.getOrderId());
        assertEquals(0, new BigDecimal("1999.98").compareTo(order.getSubtotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getDiscountAmount()));
        assertEquals(0, new BigDecimal("1999.98").compareTo(order.getTotalAmount()));
    }

    @Test
    @DisplayName("checkout consumes stock for every line")
    void checkoutDecrementsStock() {
        orderService.checkout(cartWith(LAPTOP, 4), null);

        assertEquals(LAPTOP_STOCK - 4, inventoryService.availableStock(LAPTOP));
    }

    @Test
    @DisplayName("checkout consumes the cart")
    void checkoutRemovesCart() {
        String cartId = cartWith(LAPTOP, 1);

        orderService.checkout(cartId, null);

        assertThrows(RuntimeException.class, () -> cartService.getCart(cartId));
    }

    @Test
    @DisplayName("an empty cart cannot be checked out")
    void checkoutRejectsEmptyCart() {
        Cart empty = cartService.createCart();

        assertThrows(EmptyCartException.class, () -> orderService.checkout(empty.getCartId(), null));
    }

    @Test
    @DisplayName("checkout fails when stock ran out after the cart was filled")
    void checkoutRejectsCartBeyondStock() {
        String cartId = cartWith(LAPTOP, 5);
        // Someone else takes the units between add-to-cart and checkout.
        inventoryService.reserve(Map.of(LAPTOP, LAPTOP_STOCK - 2));

        assertThrows(InsufficientStockException.class, () -> orderService.checkout(cartId, null));
        assertEquals(2, inventoryService.availableStock(LAPTOP));
    }

    @Test
    @DisplayName("a checkout that fails after reserving gives the stock back")
    void failedCheckoutRestoresStock() {
        String cartId = cartWith(LAPTOP, 3);

        assertThrows(InvalidDiscountCodeException.class,
                () -> orderService.checkout(cartId, "NOT-A-REAL-CODE"));

        assertEquals(LAPTOP_STOCK, inventoryService.availableStock(LAPTOP));
    }

    @Test
    @DisplayName("every nth order mints a coupon")
    void nthOrderGeneratesCoupon() {
        for (int i = 0; i < NTH_ORDER - 1; i++) {
            orderService.checkout(cartWith(MOUSE, 1), null);
        }
        assertTrue(discountCodeRepository.findAll().isEmpty());

        orderService.checkout(cartWith(MOUSE, 1), null);

        assertEquals(1, discountCodeRepository.findAll().size());
    }

    @Test
    @DisplayName("a minted coupon takes ten percent off")
    void couponAppliesDiscount() {
        for (int i = 0; i < NTH_ORDER; i++) {
            orderService.checkout(cartWith(MOUSE, 1), null);
        }
        String code = discountCodeRepository.findAll().get(0).getCode();

        Order order = orderService.checkout(cartWith(LAPTOP, 1), code);

        assertEquals(0, new BigDecimal("999.99").compareTo(order.getSubtotal()));
        assertEquals(0, new BigDecimal("100.00").compareTo(order.getDiscountAmount()));
        assertEquals(0, new BigDecimal("899.99").compareTo(order.getTotalAmount()));
        assertEquals(code, order.getDiscountCode());
    }

    @Test
    @DisplayName("an unknown coupon is rejected")
    void unknownCouponRejected() {
        assertThrows(InvalidDiscountCodeException.class,
                () -> orderService.checkout(cartWith(LAPTOP, 1), "MADE-UP"));
    }

    @Test
    @DisplayName("a coupon cannot be redeemed twice")
    void couponCannotBeReusedSequentially() {
        for (int i = 0; i < NTH_ORDER; i++) {
            orderService.checkout(cartWith(MOUSE, 1), null);
        }
        DiscountCode coupon = discountCodeRepository.findAll().get(0);
        orderService.checkout(cartWith(LAPTOP, 1), coupon.getCode());

        String secondCart = cartWith(LAPTOP, 1);
        assertThrows(InvalidDiscountCodeException.class,
                () -> orderService.checkout(secondCart, coupon.getCode()));
    }
}
