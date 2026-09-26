package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsResponse {
    private int totalOrders;
    private int totalItemsPurchased;
    private BigDecimal totalPurchaseAmount;
    private List<String> discountCodes;
    private BigDecimal totalDiscountAmount;
}
