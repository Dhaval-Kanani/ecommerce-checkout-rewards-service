package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Catalogue entry. {@code stock} is the authoritative number of units still
 * sellable.
 *
 * <p>Stock must only be read or written through {@link
 * com.ecommerce.service.InventoryService}, which serialises access per item.
 * Reading it directly off this object races with concurrent checkouts.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Item {
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private int stock;
}
