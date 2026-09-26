package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscountCode {
    private String code;
    private int orderNumber;
    private boolean used;
    private LocalDateTime generatedAt;
    private LocalDateTime usedAt;
    private BigDecimal discountPercentage;
    
    public DiscountCode(String code, int orderNumber, BigDecimal discountPercentage) {
        this.code = code;
        this.orderNumber = orderNumber;
        this.discountPercentage = discountPercentage;
        this.used = false;
        this.generatedAt = LocalDateTime.now();
    }
}
