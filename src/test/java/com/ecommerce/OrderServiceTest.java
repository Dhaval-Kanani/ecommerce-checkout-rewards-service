package com.ecommerce.service;

import com.ecommerce.exception.InvalidDiscountCodeException;
import com.ecommerce.model.Cart;
import com.ecommerce.model.CartItem;
import com.ecommerce.model.DiscountCode;
import com.ecommerce.model.Order;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.DiscountCodeRepository;
import com.ecommerce.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private DiscountCodeRepository discountCodeRepository;

    @Mock
    private CartService cartService;

    @InjectMocks
    private OrderService orderService;

    private Cart cart;
    private DiscountCode discountCode;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "nthOrder", 3);
        
        cart = new Cart();
        cart.getItems().add(new CartItem("ITEM001", "Laptop", new BigDecimal("100.00"), 2));
        
        discountCode = new DiscountCode("DISCOUNT10-TEST", 3, new BigDecimal("10"));
    }

    @Test
    void checkout_WithoutDiscount_ShouldCreateOrder() {
        when(cartService.getCart("cart123")).thenReturn(cart);
        when(orderRepository.getNextOrderNumber()).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.checkout("cart123", null);

        assertNotNull(result);
        assertEquals(new BigDecimal("200.00"), result.getSubtotal());
        assertEquals(BigDecimal.ZERO, result.getDiscountAmount());
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(cartRepository, times(1)).deleteById("cart123");
    }

    @Test
    void checkout_WithValidDiscount_ShouldApplyDiscount() {
        when(cartService.getCart("cart123")).thenReturn(cart);
        when(discountCodeRepository.findByCode("DISCOUNT10-TEST")).thenReturn(Optional.of(discountCode));
        when(discountCodeRepository.getCurrentActiveCode()).thenReturn(Optional.of(discountCode));
        when(orderRepository.getNextOrderNumber()).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.checkout("cart123", "DISCOUNT10-TEST");

        assertNotNull(result);
        assertEquals(new BigDecimal("200.00"), result.getSubtotal());
        assertEquals(new BigDecimal("20.00"), result.getDiscountAmount());
        assertEquals(new BigDecimal("180.00"), result.getTotalAmount());
        assertTrue(discountCode.isUsed());
    }

    @Test
    void checkout_WithInvalidDiscount_ShouldThrowException() {
        when(cartService.getCart("cart123")).thenReturn(cart);
        when(discountCodeRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        assertThrows(InvalidDiscountCodeException.class, 
                () -> orderService.checkout("cart123", "INVALID"));
    }

    @Test
    void checkout_WithUsedDiscount_ShouldThrowException() {
        discountCode.setUsed(true);
        when(cartService.getCart("cart123")).thenReturn(cart);
        when(discountCodeRepository.findByCode("DISCOUNT10-TEST")).thenReturn(Optional.of(discountCode));

        assertThrows(InvalidDiscountCodeException.class, 
                () -> orderService.checkout("cart123", "DISCOUNT10-TEST"));
    }

    @Test
    void checkout_OnThirdOrder_ShouldGenerateDiscountCode() {
        when(cartService.getCart("cart123")).thenReturn(cart);
        when(orderRepository.getNextOrderNumber()).thenReturn(3);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        orderService.checkout("cart123", null);

        verify(discountCodeRepository, times(1)).save(any(DiscountCode.class));
    }
}
