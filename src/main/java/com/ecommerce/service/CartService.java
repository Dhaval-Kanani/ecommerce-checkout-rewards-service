package com.ecommerce.service;

import com.ecommerce.exception.EmptyCartException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.Cart;
import com.ecommerce.model.CartItem;
import com.ecommerce.model.Item;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {
    
    private final CartRepository cartRepository;
    private final ItemRepository itemRepository;
    
    public Cart createCart() {
        Cart cart = new Cart();
        return cartRepository.save(cart);
    }
    
    public Cart getCart(String cartId) {
        return cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found with id: " + cartId));
    }
    
    public Cart addItemToCart(String cartId, String itemId, int quantity) {
        Cart cart;
        if (cartId == null || cartId.isEmpty()) {
            cart = createCart();
        } else {
            cart = getCart(cartId);
        }
        
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + itemId));
        
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(ci -> ci.getItemId().equals(itemId))
                .findFirst();
        
        if (existingItem.isPresent()) {
            CartItem cartItem = existingItem.get();
            cartItem.setQuantity(cartItem.getQuantity() + quantity);
        } else {
            CartItem cartItem = new CartItem(item.getId(), item.getName(), item.getPrice(), quantity);
            cart.getItems().add(cartItem);
        }
        
        return cartRepository.save(cart);
    }
    
    public Cart removeItemFromCart(String cartId, String itemId) {
        Cart cart = getCart(cartId);
        
        cart.getItems().removeIf(item -> item.getItemId().equals(itemId));
        
        return cartRepository.save(cart);
    }
    
    public void validateCart(Cart cart) {
        if (cart.getItems().isEmpty()) {
            throw new EmptyCartException("Cart is empty. Add items before checkout.");
        }
    }
}
