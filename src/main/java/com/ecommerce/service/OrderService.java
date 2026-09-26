package com.ecommerce.service;

import com.ecommerce.exception.InvalidDiscountCodeException;
import com.ecommerce.model.Cart;
import com.ecommerce.model.DiscountCode;
import com.ecommerce.model.Order;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.DiscountCodeRepository;
import com.ecommerce.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final CartService cartService;
    
    @Value("${app.discount.nth-order:3}")
    private int nthOrder;
    
    private static final BigDecimal DISCOUNT_PERCENTAGE = new BigDecimal("10");
    
    public Order checkout(String cartId, String discountCodeStr) {
        Cart cart = cartService.getCart(cartId);
        cartService.validateCart(cart);
        
        BigDecimal subtotal = cart.getTotal();
        BigDecimal discountAmount = BigDecimal.ZERO;
        String appliedDiscountCode = null;
        
        if (discountCodeStr != null && !discountCodeStr.isEmpty()) {
            DiscountCode discountCode = validateAndGetDiscountCode(discountCodeStr);
            discountAmount = subtotal.multiply(discountCode.getDiscountPercentage())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            appliedDiscountCode = discountCodeStr;
            
            discountCode.setUsed(true);
            discountCode.setUsedAt(LocalDateTime.now());
            discountCodeRepository.save(discountCode);
            discountCodeRepository.clearActiveCode();
        }
        
        BigDecimal totalAmount = subtotal.subtract(discountAmount);
        int orderNumber = orderRepository.getNextOrderNumber();
        
        Order order = new Order(
                UUID.randomUUID().toString(),
                cartId,
                new ArrayList<>(cart.getItems()),
                subtotal,
                discountAmount,
                totalAmount,
                appliedDiscountCode,
                LocalDateTime.now(),
                orderNumber
        );
        
        orderRepository.save(order);
        cartRepository.deleteById(cartId);
        
        if (orderNumber % nthOrder == 0) {
            generateDiscountCode(orderNumber);
        }
        
        return order;
    }
    
    private DiscountCode validateAndGetDiscountCode(String code) {
        DiscountCode discountCode = discountCodeRepository.findByCode(code)
                .orElseThrow(() -> new InvalidDiscountCodeException("Invalid discount code"));
        
        if (discountCode.isUsed()) {
            throw new InvalidDiscountCodeException("Discount code has already been used");
        }
        
        Optional<DiscountCode> activeCode = discountCodeRepository.getCurrentActiveCode();
        if (activeCode.isEmpty() || !activeCode.get().getCode().equals(code)) {
            throw new InvalidDiscountCodeException("This discount code is not currently active");
        }
        
        return discountCode;
    }
    
    private void generateDiscountCode(int orderNumber) {
        String code = "DISCOUNT" + DISCOUNT_PERCENTAGE.intValue() + "-" + 
                      UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DiscountCode discountCode = new DiscountCode(code, orderNumber, DISCOUNT_PERCENTAGE);
        discountCodeRepository.save(discountCode);
    }
}
