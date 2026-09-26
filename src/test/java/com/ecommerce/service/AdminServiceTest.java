package com.ecommerce.service;

import com.ecommerce.dto.AdminStatsResponse;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.DiscountCodeRepository;
import com.ecommerce.repository.IdempotencyRepository;
import com.ecommerce.repository.ItemRepository;
import com.ecommerce.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminServiceTest {

    private static final String LAPTOP = "ITEM001"; // 999.99
    private static final String MOUSE = "ITEM002";  // 29.99
    private static final int NTH_ORDER = 3;

    private CartService cartService;
    private OrderService orderService;
    private AdminService adminService;
    private DiscountCodeRepository discountCodeRepository;

    @BeforeEach
    void setUp() {
        ItemRepository itemRepository = new ItemRepository();
        CartRepository cartRepository = new CartRepository();
        InventoryService inventoryService = new InventoryService(itemRepository);
        cartService = new CartService(cartRepository, itemRepository, inventoryService);
        OrderRepository orderRepository = new OrderRepository();
        discountCodeRepository = new DiscountCodeRepository();
        orderService = new OrderService(orderRepository, cartRepository, discountCodeRepository,
                new IdempotencyRepository(), cartService, inventoryService);
        ReflectionTestUtils.setField(orderService, "nthOrder", NTH_ORDER);
        adminService = new AdminService(orderRepository, discountCodeRepository);
    }

    private void checkout(String itemId, int quantity, String code) {
        String cartId = cartService.addItemToCart(null, itemId, quantity).getCartId();
        orderService.checkout(cartId, code, UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("the report separates gross revenue, discount and net revenue")
    void reportsRevenueBeforeAndAfterDiscount() {
        for (int i = 0; i < NTH_ORDER; i++) {
            checkout(MOUSE, 1, null);
        }
        String code = discountCodeRepository.findAll().get(0).getCode();
        checkout(LAPTOP, 1, code);

        AdminStatsResponse stats = adminService.getStatistics();

        assertEquals(4, stats.getTotalOrders());
        assertEquals(4, stats.getTotalItemsPurchased());
        assertEquals(0, new BigDecimal("1089.96").compareTo(stats.getGrossRevenue()));
        assertEquals(0, new BigDecimal("100.00").compareTo(stats.getTotalDiscountAmount()));
        assertEquals(0, new BigDecimal("989.96").compareTo(stats.getNetRevenue()));
        assertEquals(1, stats.getDiscountedOrders());
    }

    @Test
    @DisplayName("the report counts coupons by lifecycle stage")
    void reportsCouponLifecycle() {
        for (int i = 0; i < NTH_ORDER * 2; i++) {
            checkout(MOUSE, 1, null);
        }
        String firstCode = discountCodeRepository.findAll().get(0).getCode();
        checkout(LAPTOP, 1, firstCode);

        AdminStatsResponse stats = adminService.getStatistics();

        assertEquals(2, stats.getDiscountCodes().size());
        assertEquals(1, stats.getCouponsIssued());
        assertEquals(0, stats.getCouponsReserved());
        assertEquals(1, stats.getCouponsRedeemed());
    }
}
