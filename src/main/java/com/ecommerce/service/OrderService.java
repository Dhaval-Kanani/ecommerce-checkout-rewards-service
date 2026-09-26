package com.ecommerce.service;

import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.Cart;
import com.ecommerce.model.DiscountCode;
import com.ecommerce.model.Order;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.DiscountCodeRepository;
import com.ecommerce.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final BigDecimal DISCOUNT_PERCENTAGE = new BigDecimal("10");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final CartService cartService;
    private final InventoryService inventoryService;

    @Value("${app.discount.nth-order:3}")
    private int nthOrder;

    /**
     * Turns a cart into an order.
     *
     * <p>Every contended resource is claimed before the order is built and
     * released again if anything afterwards throws. The coupon in particular is
     * only marked redeemed once the order exists, so a checkout that fails on a
     * later step does not burn it.
     */
    public Order checkout(String cartId, String discountCodeStr) {
        // Claim the cart first. Of two concurrent checkouts of the same cart only
        // one gets it, so a single cart can never become two orders.
        Cart cart = cartRepository.claim(cartId).orElseThrow(() -> new ResourceNotFoundException(
                "Cart not found, or already checked out: " + cartId));

        boolean committed = false;
        boolean stockReserved = false;
        String reservedCoupon = null;
        Map<String, Integer> lines = null;

        try {
            cartService.validateCart(cart);

            lines = cart.lineQuantities();
            inventoryService.reserve(lines);
            // Set only after reserve returns. Restoring stock we never took would
            // invent inventory out of nothing.
            stockReserved = true;

            BigDecimal subtotal = cart.getTotal();
            BigDecimal discountAmount = BigDecimal.ZERO;
            String appliedDiscountCode = null;

            if (discountCodeStr != null && !discountCodeStr.isEmpty()) {
                // Claims the coupon. Exactly one concurrent checkout wins.
                DiscountCode coupon = discountCodeRepository.reserve(discountCodeStr);
                reservedCoupon = discountCodeStr;
                discountAmount = subtotal.multiply(coupon.getDiscountPercentage())
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP);
                appliedDiscountCode = discountCodeStr;
            }

            int orderNumber = orderRepository.getNextOrderNumber();
            Order order = new Order(
                    UUID.randomUUID().toString(),
                    cartId,
                    new ArrayList<>(cart.getItems()),
                    subtotal,
                    discountAmount,
                    subtotal.subtract(discountAmount),
                    appliedDiscountCode,
                    LocalDateTime.now(),
                    orderNumber
            );
            orderRepository.save(order);

            if (reservedCoupon != null) {
                discountCodeRepository.markRedeemed(reservedCoupon);
            }
            committed = true;

            mintMilestoneCoupon(orderNumber);
            return order;
        } finally {
            if (!committed) {
                // Undo in the reverse order of acquisition.
                if (reservedCoupon != null) {
                    discountCodeRepository.release(reservedCoupon);
                }
                if (stockReserved) {
                    inventoryService.restore(lines);
                }
                // Hand the cart back so the customer can fix the problem and retry.
                cartRepository.save(cart);
            }
        }
    }

    /**
     * Mints the milestone coupon. Runs after the order is committed, and a failure
     * here must not fail a paid order, so it is logged rather than propagated.
     */
    private void mintMilestoneCoupon(int orderNumber) {
        if (orderNumber % nthOrder != 0) {
            return;
        }
        try {
            String code = "DISCOUNT" + DISCOUNT_PERCENTAGE.intValue() + "-"
                    + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            discountCodeRepository.save(new DiscountCode(code, orderNumber, DISCOUNT_PERCENTAGE));
        } catch (RuntimeException e) {
            log.error("Failed to mint milestone coupon for order number {}", orderNumber, e);
        }
    }
}
