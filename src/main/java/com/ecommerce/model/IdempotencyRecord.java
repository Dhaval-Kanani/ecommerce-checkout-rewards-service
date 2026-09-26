package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * What the service remembers about one idempotency key.
 *
 * <p>{@code fingerprint} is a digest of the request the key first arrived with.
 * Reusing a key for a different cart or coupon is a client bug, and comparing
 * fingerprints turns it into a clear error instead of silently replaying an
 * unrelated order.
 *
 * <p>Mutated only inside {@link
 * com.ecommerce.repository.IdempotencyRepository}, under the map's per-key lock.
 */
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
