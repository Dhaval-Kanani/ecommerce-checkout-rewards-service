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

/**
 * Sole owner of item stock.
 *
 * <p>Invariants guaranteed here:
 * <ul>
 *   <li>Stock never drops below zero, so the service cannot oversell.</li>
 *   <li>A multi-line reservation is all-or-nothing: either every line is
 *       decremented or none is.</li>
 * </ul>
 *
 * <p>A reservation locks every item it touches before checking any of them,
 * because checking and decrementing must not be separable. Locks are always
 * acquired in ascending item-id order. That total ordering is what makes
 * concurrent multi-item reservations deadlock-free.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ItemRepository itemRepository;

    /** One lock per item id, created on first use. */
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Atomically decrements stock for every line, or throws and changes nothing.
     *
     * @param lines item id to unit count
     * @throws InsufficientStockException if any line exceeds available stock
     * @throws ResourceNotFoundException  if any item id is unknown
     */
    public void reserve(Map<String, Integer> lines) {
        List<ReentrantLock> held = acquire(lines.keySet());
        try {
            // Check every line first. Decrementing as we go would leave a
            // partial reservation behind when a later line fails.
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

    /**
     * Returns previously reserved units to the catalogue. Used to compensate a
     * checkout that failed after reserving.
     */
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

    /**
     * Stock for one item, read under its lock so it is never a torn value.
     *
     * <p>Advisory only: by the time a caller acts on it another thread may have
     * reserved those units. {@link #reserve} is the authoritative check.
     */
    public int availableStock(String itemId) {
        ReentrantLock lock = lockFor(itemId);
        lock.lock();
        try {
            return require(itemId).getStock();
        } finally {
            lock.unlock();
        }
    }

    /** Catalogue snapshot with per-item consistent stock values. */
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

    /** Locks each distinct item id in ascending order. Deadlock-free by construction. */
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
