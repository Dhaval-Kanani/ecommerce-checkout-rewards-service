package com.ecommerce.exception;

/** Raised when a cart line asks for more units than the catalogue can supply. */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
