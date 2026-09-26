package com.ecommerce.exception;

public class CouponUnavailableException extends RuntimeException {
    public CouponUnavailableException(String message) {
        super(message);
    }
}
