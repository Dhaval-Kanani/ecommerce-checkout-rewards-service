package com.ecommerce.exception;

/** Raised when an idempotency key is reused with a different request payload. */
public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException(String message) {
        super(message);
    }
}
