package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class IdempotencyRecord {
    private String key;
    private String fingerprint;
    private IdempotencyStatus status;
    private String orderId;
    private LocalDateTime createdAt;

    public static IdempotencyRecord inProgress(String key, String fingerprint) {
        return new IdempotencyRecord(key, fingerprint, IdempotencyStatus.IN_PROGRESS, null,
                LocalDateTime.now());
    }
}
