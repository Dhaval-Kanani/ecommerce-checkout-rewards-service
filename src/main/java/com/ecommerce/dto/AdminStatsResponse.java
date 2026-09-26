package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sales and coupon report.
 *
 * <p>Revenue is reported both before and after discounts. Reporting only the net
 * figure makes it impossible to tell a quiet month from a heavily discounted one.
 */
@Data
@AllArgsConstructor
public class AdminStatsResponse {
    private int totalOrders;
    private int totalItemsPurchased;

    /** Sum of order subtotals, before any discount. */
    private BigDecimal grossRevenue;
    /** Total taken off by coupons. */
    private BigDecimal totalDiscountAmount;
    /** Sum of order totals, after discounts. */
    private BigDecimal netRevenue;

    /** Orders that redeemed a coupon. */
    private int discountedOrders;

    /** Coupons minted and still available. */
    private long couponsIssued;
    /** Coupons held by a checkout that has not finished. Normally zero at rest. */
    private long couponsReserved;
    /** Coupons spent. Terminal. */
    private long couponsRedeemed;

    private List<String> discountCodes;
}
