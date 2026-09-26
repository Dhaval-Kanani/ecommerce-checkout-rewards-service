package com.ecommerce.service;

import com.ecommerce.dto.AdminStatsResponse;
import com.ecommerce.model.CouponStatus;
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
        List<Order> orders = orderRepository.findAll();

        int totalItemsPurchased = orders.stream()
                .mapToInt(order -> order.getItems().stream()
                        .mapToInt(line -> line.getQuantity())
                        .sum())
                .sum();

        BigDecimal grossRevenue = sum(orders, Order::getSubtotal);
        BigDecimal totalDiscount = sum(orders, Order::getDiscountAmount);
        BigDecimal netRevenue = sum(orders, Order::getTotalAmount);

        int discountedOrders = (int) orders.stream()
                .filter(order -> order.getDiscountCode() != null)
                .count();

        List<String> codes = discountCodeRepository.findAll().stream()
                .map(DiscountCode::getCode)
                .collect(Collectors.toList());

        return new AdminStatsResponse(
                orders.size(),
                totalItemsPurchased,
                grossRevenue,
                totalDiscount,
                netRevenue,
                discountedOrders,
                discountCodeRepository.countByStatus(CouponStatus.ISSUED),
                discountCodeRepository.countByStatus(CouponStatus.RESERVED),
                discountCodeRepository.countByStatus(CouponStatus.REDEEMED),
                codes
        );
    }

    public List<DiscountCode> getAllDiscountCodes() {
        return discountCodeRepository.findAll();
    }

    private BigDecimal sum(List<Order> orders, java.util.function.Function<Order, BigDecimal> field) {
        return orders.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
