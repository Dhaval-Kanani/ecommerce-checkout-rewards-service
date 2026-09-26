package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single-use percentage coupon.
 *
 * <p>{@code status} must only be changed through {@link
 * com.ecommerce.repository.DiscountCodeRepository}, which performs each
 * transition atomically. Setting it directly reintroduces the double-redeem
 * race this state machine exists to prevent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscountCode {
    private String code;
    private int orderNumber;
    private CouponStatus status;
    private LocalDateTime issuedAt;
    private LocalDateTime redeemedAt;
    private BigDecimal discountPercentage;

    public DiscountCode(String code, int orderNumber, BigDecimal discountPercentage) {
        this.code = code;
        this.orderNumber = orderNumber;
        this.discountPercentage = discountPercentage;
        this.status = CouponStatus.ISSUED;
        this.issuedAt = LocalDateTime.now();
    }

    public boolean isRedeemed() {
        return status == CouponStatus.REDEEMED;
    }
}
