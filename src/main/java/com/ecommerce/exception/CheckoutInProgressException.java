package com.ecommerce.exception;

public class CheckoutInProgressException extends RuntimeException {
    public CheckoutInProgressException(String message) {
        super(message);
    }
}
