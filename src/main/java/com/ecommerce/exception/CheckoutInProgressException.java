package com.ecommerce.exception;

/**
 * Raised when a request arrives with an idempotency key whose checkout is still
 * running. The client should retry; the first attempt has not resolved yet.
 */
public class CheckoutInProgressException extends RuntimeException {
    public CheckoutInProgressException(String message) {
        super(message);
    }
}
