package com.ecommerce.repository;

import com.ecommerce.model.IdempotencyRecord;
import com.ecommerce.model.IdempotencyStatus;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which idempotency keys have been seen and what they produced.
 *
 * <p>The whole design rests on {@code putIfAbsent} being atomic: of any number
 * of concurrent requests carrying one key, exactly one inserts the record and
 * therefore exactly one is allowed to place an order.
 */
@Repository
public class IdempotencyRepository {

    private final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

    /**
     * Attempts to take ownership of a key.
     *
     * @return empty when the caller now owns the key and should place the order;
     *         otherwise the record that already exists for it
     */
    public Optional<IdempotencyRecord> begin(String key, String fingerprint) {
        return Optional.ofNullable(records.putIfAbsent(key, IdempotencyRecord.inProgress(key, fingerprint)));
    }

    /** Records the order this key produced, so later retries replay it. */
    public void complete(String key, String orderId) {
        records.compute(key, (k, record) -> {
            if (record == null) {
                return null;
            }
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setOrderId(orderId);
            return record;
        });
    }

    /**
     * Forgets a key whose checkout failed, so the client can retry with the same
     * key. Keeping it would permanently block a legitimate retry.
     */
    public void abandon(String key) {
        records.remove(key);
    }

    public Optional<IdempotencyRecord> find(String key) {
        return Optional.ofNullable(records.get(key));
    }

    public int size() {
        return records.size();
    }
}
