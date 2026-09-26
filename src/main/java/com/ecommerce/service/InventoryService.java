package com.ecommerce.service;

import com.ecommerce.exception.InsufficientStockException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.Item;
import com.ecommerce.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final ItemRepository itemRepository;

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public void reserve(Map<String, Integer> lines) {
        List<ReentrantLock> held = acquire(lines.keySet());
        try {
            for (Map.Entry<String, Integer> line : lines.entrySet()) {
                Item item = require(line.getKey());
                if (item.getStock() < line.getValue()) {
                    throw new InsufficientStockException(
                            "Insufficient stock for " + item.getName() + ": requested "
                                    + line.getValue() + ", available " + item.getStock());
                }
            }
            for (Map.Entry<String, Integer> line : lines.entrySet()) {
                Item item = require(line.getKey());
                item.setStock(item.getStock() - line.getValue());
                itemRepository.save(item);
            }
        } finally {
            releaseLocks(held);
        }
    }

    public void restore(Map<String, Integer> lines) {
        List<ReentrantLock> held = acquire(lines.keySet());
        try {
            for (Map.Entry<String, Integer> line : lines.entrySet()) {
                Item item = require(line.getKey());
                item.setStock(item.getStock() + line.getValue());
                itemRepository.save(item);
            }
        } finally {
            releaseLocks(held);
        }
    }

    public int availableStock(String itemId) {
        ReentrantLock lock = lockFor(itemId);
        lock.lock();
        try {
            return require(itemId).getStock();
        } finally {
            lock.unlock();
        }
    }

    public List<Item> catalogue() {
        List<Item> snapshot = new ArrayList<>();
        for (Item item : itemRepository.findAll()) {
            snapshot.add(new Item(item.getId(), item.getName(), item.getDescription(),
                    item.getPrice(), availableStock(item.getId())));
        }
        snapshot.sort(Comparator.comparing(Item::getId));
        return snapshot;
    }

    private ReentrantLock lockFor(String itemId) {
        return locks.computeIfAbsent(itemId, key -> new ReentrantLock());
    }

    private List<ReentrantLock> acquire(Collection<String> itemIds) {
        List<String> ordered = new ArrayList<>(new TreeSet<>(itemIds));
        List<ReentrantLock> held = new ArrayList<>(ordered.size());
        try {
            for (String itemId : ordered) {
                ReentrantLock lock = lockFor(itemId);
                lock.lock();
                held.add(lock);
            }
        } catch (RuntimeException | Error e) {
            releaseLocks(held);
            throw e;
        }
        return held;
    }

    private void releaseLocks(List<ReentrantLock> held) {
        for (int i = held.size() - 1; i >= 0; i--) {
            held.get(i).unlock();
        }
    }

    private Item require(String itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + itemId));
    }
}
