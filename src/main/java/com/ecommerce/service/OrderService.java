package com.ecommerce.service;

import com.ecommerce.dto.CheckoutResult;
import com.ecommerce.exception.CheckoutInProgressException;
import com.ecommerce.exception.IdempotencyKeyConflictException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.Cart;
import com.ecommerce.model.DiscountCode;
import com.ecommerce.model.IdempotencyRecord;
import com.ecommerce.model.IdempotencyStatus;
import com.ecommerce.model.Order;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.DiscountCodeRepository;
import com.ecommerce.repository.IdempotencyRepository;
import com.ecommerce.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
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
    private final IdempotencyRepository idempotencyRepository;
    private final CartService cartService;
    private final InventoryService inventoryService;

    @Value("${app.discount.nth-order:3}")
    private int nthOrder;

    public CheckoutResult checkout(String cartId, String discountCodeStr, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key must not be blank");
        }

        String fingerprint = fingerprint(cartId, discountCodeStr);
        Optional<IdempotencyRecord> existing = idempotencyRepository.begin(idempotencyKey, fingerprint);

        if (existing.isPresent()) {
            return replay(existing.get(), idempotencyKey, fingerprint);
        }

        try {
            Order order = placeOrder(cartId, discountCodeStr);
            idempotencyRepository.complete(idempotencyKey, order.getOrderId());
            return new CheckoutResult(order, false);
        } catch (RuntimeException e) {
            idempotencyRepository.abandon(idempotencyKey);
            throw e;
        }
    }

    private CheckoutResult replay(IdempotencyRecord record, String idempotencyKey, String fingerprint) {
        if (!record.getFingerprint().equals(fingerprint)) {
            throw new IdempotencyKeyConflictException("Idempotency-Key " + idempotencyKey
                    + " was already used for a different checkout request");
        }
        if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
            throw new CheckoutInProgressException("A checkout for Idempotency-Key " + idempotencyKey
                    + " is still in progress. Retry shortly.");
        }
        Order order = orderRepository.findById(record.getOrderId())
                .orElseThrow(() -> new IllegalStateException("Idempotency-Key " + idempotencyKey
                        + " references missing order " + record.getOrderId()));
        return new CheckoutResult(order, true);
    }

    private Order placeOrder(String cartId, String discountCodeStr) {
        Cart cart = cartRepository.claim(cartId).orElseThrow(() -> new ResourceNotFoundException(
                "Cart not found, or already checked out: " + cartId));

        boolean committed = false;
        boolean stockReserved = false;
        String reservedCoupon = null;
        String savedOrderId = null;
        Map<String, Integer> lines = null;

        try {
            cartService.validateCart(cart);

            lines = cart.lineQuantities();
            inventoryService.reserve(lines);

            stockReserved = true;

            BigDecimal subtotal = cart.getTotal();
            BigDecimal discountAmount = BigDecimal.ZERO;
            String appliedDiscountCode = null;

            if (discountCodeStr != null && !discountCodeStr.isEmpty()) {
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
            savedOrderId = order.getOrderId();

            if (reservedCoupon != null) {
                discountCodeRepository.markRedeemed(reservedCoupon);
            }
            committed = true;

            mintMilestoneCoupon(orderNumber);
            return order;
        } finally {
            if (!committed) {
                if (savedOrderId != null) {
                    orderRepository.deleteById(savedOrderId);
                }
                if (reservedCoupon != null) {
                    discountCodeRepository.release(reservedCoupon);
                }
                if (stockReserved) {
                    inventoryService.restore(lines);
                }

                cartRepository.save(cart);
            }
        }
    }

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

    private String fingerprint(String cartId, String discountCodeStr) {
        String cart = cartId == null ? "" : cartId;
        String code = discountCodeStr == null ? "" : discountCodeStr;
        String canonical = cart.length() + ":" + cart + "/" + code.length() + ":" + code;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
