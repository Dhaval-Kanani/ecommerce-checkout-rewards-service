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

    /**
     * Takes the cart out of the store and hands it to the caller, atomically.
     *
     * <p>Checkout uses this to claim ownership. {@code remove} returns the previous
     * value under the key's lock, so of two concurrent checkouts of one cart
     * exactly one receives it and the other sees an empty result. Without this,
     * both would price and reserve the same cart and produce two orders from it.
     *
     * <p>A caller that claims a cart and then fails must put it back with
     * {@link #save}.
     */
    public Optional<Cart> claim(String cartId) {
        return Optional.ofNullable(carts.remove(cartId));
    }

    /**
     * Applies a mutation to one cart while holding that key's map lock, so two
     * concurrent writes to the same cart cannot interleave.
     *
     * <p>The mutation runs inside {@code compute}, so it must stay short and must
     * not touch other keys of this map.
     */
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
