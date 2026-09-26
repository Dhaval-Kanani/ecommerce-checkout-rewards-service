package com.ecommerce.model;

/**
 * Lifecycle of a discount coupon.
 *
 * <p>Legal transitions, and only these:
 * <pre>
 *   ISSUED   -> RESERVED   a checkout has claimed it
 *   RESERVED -> REDEEMED   that checkout committed
 *   RESERVED -> ISSUED     that checkout failed, the coupon is available again
 * </pre>
 *
 * REDEEMED is terminal, which is what makes a coupon single-use.
 */
public enum CouponStatus {
    ISSUED,
    RESERVED,
    REDEEMED
}
