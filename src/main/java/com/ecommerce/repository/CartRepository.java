package com.ecommerce.repository;

import com.ecommerce.model.Cart;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class CartRepository {
    
    private final Map<String, Cart> carts = new ConcurrentHashMap<>();
    
    public Cart save(Cart cart) {
        carts.put(cart.getCartId(), cart);
        return cart;
    }
    
    public Optional<Cart> findById(String cartId) {
        return Optional.ofNullable(carts.get(cartId));
    }
    
    public void deleteById(String cartId) {
        carts.remove(cartId);
    }
}
