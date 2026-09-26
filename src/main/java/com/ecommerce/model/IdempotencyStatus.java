package com.ecommerce.model;

/** State of a checkout attempt tracked against an idempotency key. */
public enum IdempotencyStatus {
    /** A checkout owns this key and has not finished. */
    IN_PROGRESS,
    /** The checkout committed. The recorded order id is the one to replay. */
    COMPLETED
}
