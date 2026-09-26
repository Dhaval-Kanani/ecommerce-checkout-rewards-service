package com.ecommerce.repository;

import com.ecommerce.model.Order;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class OrderRepository {
    
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicInteger orderCounter = new AtomicInteger(0);
    
    public Order save(Order order) {
        orders.put(order.getOrderId(), order);
        return order;
    }
    
    /**
     * Removes an order. Used only to compensate a checkout that saved the order
     * and then failed before its commit point, so a rolled-back attempt cannot
     * leave a phantom order in the store.
     */
    public void deleteById(String orderId) {
        orders.remove(orderId);
    }

    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }
    
    public List<Order> findAll() {
        return new ArrayList<>(orders.values());
    }
    
    public int getNextOrderNumber() {
        return orderCounter.incrementAndGet();
    }
    
    public int getCurrentOrderCount() {
        return orderCounter.get();
    }
}
