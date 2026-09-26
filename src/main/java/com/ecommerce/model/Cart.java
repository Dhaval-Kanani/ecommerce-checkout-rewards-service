package com.ecommerce.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class Cart {
    private String cartId;
    private List<CartItem> items;

    public Cart() {
        this.cartId = UUID.randomUUID().toString();
        this.items = new ArrayList<>();
    }

    public BigDecimal getTotal() {
        return items.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getTotalItems() {
        return items.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }

    /**
     * Units required per item id, collapsed so one item cannot appear twice.
     * This is the shape the inventory reservation needs.
     */
    public Map<String, Integer> lineQuantities() {
        Map<String, Integer> lines = new LinkedHashMap<>();
        for (CartItem item : items) {
            lines.merge(item.getItemId(), item.getQuantity(), Integer::sum);
        }
        return lines;
    }
}
