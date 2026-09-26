package com.ecommerce.service;

import com.ecommerce.dto.AdminStatsResponse;
import com.ecommerce.model.DiscountCode;
import com.ecommerce.model.Order;
import com.ecommerce.repository.DiscountCodeRepository;
import com.ecommerce.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {
    
    private final OrderRepository orderRepository;
    private final DiscountCodeRepository discountCodeRepository;
    
    public AdminStatsResponse getStatistics() {
        List<Order> allOrders = orderRepository.findAll();
        
        int totalOrders = allOrders.size();
        
        int totalItemsPurchased = allOrders.stream()
                .mapToInt(order -> order.getItems().stream()
                        .mapToInt(item -> item.getQuantity())
                        .sum())
                .sum();
        
        BigDecimal totalPurchaseAmount = allOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        List<String> discountCodes = discountCodeRepository.findAll().stream()
                .map(DiscountCode::getCode)
                .collect(Collectors.toList());
        
        BigDecimal totalDiscountAmount = allOrders.stream()
                .map(Order::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return new AdminStatsResponse(
                totalOrders,
                totalItemsPurchased,
                totalPurchaseAmount,
                discountCodes,
                totalDiscountAmount
        );
    }
    
    public List<DiscountCode> getAllDiscountCodes() {
        return discountCodeRepository.findAll();
    }
}
