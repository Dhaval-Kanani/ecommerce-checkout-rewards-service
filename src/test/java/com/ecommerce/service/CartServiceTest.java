package com.ecommerce.service;

import com.ecommerce.exception.EmptyCartException;
import com.ecommerce.exception.InsufficientStockException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.Cart;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CartServiceTest {
    private static final String LAPTOP = "ITEM001";
    private static final int LAPTOP_STOCK = 50;

    private CartService cartService;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        ItemRepository itemRepository = new ItemRepository();
        inventoryService = new InventoryService(itemRepository);
        cartService = new CartService(new CartRepository(), itemRepository, inventoryService);
    }

    @Test
    @DisplayName("a new cart starts empty with an id")
    void createCartReturnsEmptyCart() {
        Cart cart = cartService.createCart();

        assertNotNull(cart.getCartId());
        assertTrue(cart.getItems().isEmpty());
    }

    @Test
    @DisplayName("an unknown cart id is rejected")
    void getCartRejectsUnknownId() {
        assertThrows(ResourceNotFoundException.class, () -> cartService.getCart("nope"));
    }

    @Test
    @DisplayName("adding with no cart id creates a cart")
    void addItemWithoutCartIdCreatesCart() {
        Cart cart = cartService.addItemToCart(null, LAPTOP, 2);

        assertNotNull(cart.getCartId());
        assertEquals(1, cart.getItems().size());
        assertEquals(2, cart.getItems().get(0).getQuantity());
    }

    @Test
    @DisplayName("adding the same item twice merges the quantities")
    void addItemTwiceMergesQuantity() {
        Cart cart = cartService.addItemToCart(null, LAPTOP, 2);
        cart = cartService.addItemToCart(cart.getCartId(), LAPTOP, 3);

        assertEquals(1, cart.getItems().size());
        assertEquals(5, cart.getItems().get(0).getQuantity());
    }

    @Test
    @DisplayName("an unknown item is rejected")
    void addItemRejectsUnknownItem() {
        assertThrows(ResourceNotFoundException.class,
                () -> cartService.addItemToCart(null, "MISSING", 1));
    }

    @Test
    @DisplayName("a quantity below one is rejected")
    void addItemRejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> cartService.addItemToCart(null, LAPTOP, 0));
    }

    @Test
    @DisplayName("a cart cannot hold more units than exist")
    void addItemRejectsQuantityBeyondStock() {
        assertThrows(InsufficientStockException.class,
                () -> cartService.addItemToCart(null, LAPTOP, LAPTOP_STOCK + 1));
    }

    @Test
    @DisplayName("the merged quantity is what gets checked against stock")
    void addItemChecksMergedQuantityAgainstStock() {
        Cart cart = cartService.addItemToCart(null, LAPTOP, LAPTOP_STOCK);

        assertThrows(InsufficientStockException.class,
                () -> cartService.addItemToCart(cart.getCartId(), LAPTOP, 1));
    }

    @Test
    @DisplayName("adding to a cart does not consume stock")
    void addItemDoesNotReserveStock() {
        cartService.addItemToCart(null, LAPTOP, 10);

        assertEquals(LAPTOP_STOCK, inventoryService.availableStock(LAPTOP));
    }

    @Test
    @DisplayName("removing an item empties the line")
    void removeItemClearsLine() {
        Cart cart = cartService.addItemToCart(null, LAPTOP, 1);

        Cart updated = cartService.removeItemFromCart(cart.getCartId(), LAPTOP);

        assertTrue(updated.getItems().isEmpty());
    }

    @Test
    @DisplayName("an empty cart cannot be checked out")
    void validateCartRejectsEmptyCart() {
        Cart cart = cartService.createCart();

        assertThrows(EmptyCartException.class, () -> cartService.validateCart(cart));
    }

    @Test
    @DisplayName("a cart with items passes validation")
    void validateCartAcceptsNonEmptyCart() {
        Cart cart = cartService.addItemToCart(null, LAPTOP, 1);

        assertDoesNotThrow(() -> cartService.validateCart(cart));
    }
}
