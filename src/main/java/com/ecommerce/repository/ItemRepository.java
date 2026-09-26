package com.ecommerce.repository;

import com.ecommerce.model.Item;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ItemRepository {
    
    private final Map<String, Item> items = new ConcurrentHashMap<>();
    
    public ItemRepository() {
        initializeSampleItems();
    }
    
    private void initializeSampleItems() {
        items.put("ITEM001", new Item("ITEM001", "Laptop", "High-performance laptop", new BigDecimal("999.99"), 50));
        items.put("ITEM002", new Item("ITEM002", "Mouse", "Wireless mouse", new BigDecimal("29.99"), 100));
        items.put("ITEM003", new Item("ITEM003", "Keyboard", "Mechanical keyboard", new BigDecimal("79.99"), 75));
        items.put("ITEM004", new Item("ITEM004", "Monitor", "27-inch 4K monitor", new BigDecimal("399.99"), 30));
        items.put("ITEM005", new Item("ITEM005", "Headphones", "Noise-cancelling headphones", new BigDecimal("199.99"), 60));
    }
    
    public Optional<Item> findById(String id) {
        return Optional.ofNullable(items.get(id));
    }
    
    public List<Item> findAll() {
        return new ArrayList<>(items.values());
    }
    
    public Item save(Item item) {
        items.put(item.getId(), item);
        return item;
    }
}
