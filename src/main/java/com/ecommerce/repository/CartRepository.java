package com.ecommerce.repository;

import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.Cart;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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

    public Optional<Cart> claim(String cartId) {
        return Optional.ofNullable(carts.remove(cartId));
    }

    public Cart mutate(String cartId, Consumer<Cart> mutation) {
        Cart updated = carts.compute(cartId, (key, cart) -> {
            if (cart == null) {
                return null;
            }
            mutation.accept(cart);
            return cart;
        });
        if (updated == null) {
            throw new ResourceNotFoundException("Cart not found with id: " + cartId);
        }
        return updated;
    }
}
