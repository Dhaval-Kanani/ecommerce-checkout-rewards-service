package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class AdminStatsResponse {
    private int totalOrders;
    private int totalItemsPurchased;

    private BigDecimal grossRevenue;

    private BigDecimal totalDiscountAmount;

    private BigDecimal netRevenue;

    private int discountedOrders;

    private long couponsIssued;

    private long couponsReserved;

    private long couponsRedeemed;

    private List<String> discountCodes;
}
