package com.ecommerce.service;

import com.ecommerce.exception.EmptyCartException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.Cart;
import com.ecommerce.model.CartItem;
import com.ecommerce.model.Item;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private CartService cartService;

    private Cart cart;
    private Item item;

    @BeforeEach
    void setUp() {
        cart = new Cart();
        item = new Item("ITEM001", "Laptop", "Test laptop", new BigDecimal("999.99"), 10);
    }

    @Test
    void createCart_ShouldReturnNewCart() {
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        Cart result = cartService.createCart();

        assertNotNull(result);
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void getCart_WithValidId_ShouldReturnCart() {
        when(cartRepository.findById("cart123")).thenReturn(Optional.of(cart));

        Cart result = cartService.getCart("cart123");

        assertNotNull(result);
        assertEquals(cart, result);
    }

    @Test
    void getCart_WithInvalidId_ShouldThrowException() {
        when(cartRepository.findById("invalid")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.getCart("invalid"));
    }

    @Test
    void addItemToCart_WithNewItem_ShouldAddSuccessfully() {
        when(cartRepository.findById("cart123")).thenReturn(Optional.of(cart));
        when(itemRepository.findById("ITEM001")).thenReturn(Optional.of(item));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        Cart result = cartService.addItemToCart("cart123", "ITEM001", 2);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        verify(cartRepository, times(1)).save(cart);
    }

    @Test
    void addItemToCart_WithInvalidItem_ShouldThrowException() {
        when(cartRepository.findById("cart123")).thenReturn(Optional.of(cart));
        when(itemRepository.findById("invalid")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, 
                () -> cartService.addItemToCart("cart123", "invalid", 2));
    }

    @Test
    void validateCart_WithEmptyCart_ShouldThrowException() {
        assertThrows(EmptyCartException.class, () -> cartService.validateCart(cart));
    }

    @Test
    void validateCart_WithItems_ShouldNotThrowException() {
        cart.getItems().add(new CartItem("ITEM001", "Laptop", new BigDecimal("999.99"), 1));

        assertDoesNotThrow(() -> cartService.validateCart(cart));
    }

    @Test
    void removeItemFromCart_ShouldRemoveSuccessfully() {
        cart.getItems().add(new CartItem("ITEM001", "Laptop", new BigDecimal("999.99"), 1));
        when(cartRepository.findById("cart123")).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        Cart result = cartService.removeItemFromCart("cart123", "ITEM001");

        assertTrue(result.getItems().isEmpty());
        verify(cartRepository, times(1)).save(cart);
    }
}
