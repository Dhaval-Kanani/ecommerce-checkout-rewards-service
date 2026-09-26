package com.ecommerce.exception;

/**
 * Raised when a coupon exists but cannot be claimed right now: it is already
 * redeemed, or another in-flight checkout holds it.
 */
public class CouponUnavailableException extends RuntimeException {
    public CouponUnavailableException(String message) {
        super(message);
    }
}
