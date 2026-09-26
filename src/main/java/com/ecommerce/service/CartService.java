package com.ecommerce.service;

import com.ecommerce.exception.EmptyCartException;
import com.ecommerce.exception.InsufficientStockException;
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
    private final InventoryService inventoryService;

    public Cart createCart() {
        Cart cart = new Cart();
        return cartRepository.save(cart);
    }

    public Cart getCart(String cartId) {
        return cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found with id: " + cartId));
    }

    public Cart addItemToCart(String cartId, String itemId, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }

        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + itemId));

        String targetCartId = (cartId == null || cartId.isEmpty())
                ? createCart().getCartId()
                : getCart(cartId).getCartId();

        return cartRepository.mutate(targetCartId, cart -> {
            Optional<CartItem> existing = cart.getItems().stream()
                    .filter(line -> line.getItemId().equals(itemId))
                    .findFirst();

            int alreadyInCart = existing.map(CartItem::getQuantity).orElse(0);
            int requestedTotal = alreadyInCart + quantity;
            int available = inventoryService.availableStock(itemId);
            if (requestedTotal > available) {
                throw new InsufficientStockException(
                        "Insufficient stock for " + item.getName() + ": requested "
                                + requestedTotal + ", available " + available);
            }

            if (existing.isPresent()) {
                existing.get().setQuantity(requestedTotal);
            } else {
                cart.getItems().add(new CartItem(item.getId(), item.getName(), item.getPrice(), quantity));
            }
        });
    }

    public Cart removeItemFromCart(String cartId, String itemId) {
        getCart(cartId);
        return cartRepository.mutate(cartId, cart -> cart.getItems().removeIf(line -> line.getItemId().equals(itemId)));
    }

    public void validateCart(Cart cart) {
        if (cart.getItems().isEmpty()) {
            throw new EmptyCartException("Cart is empty. Add items before checkout.");
        }
    }
}
