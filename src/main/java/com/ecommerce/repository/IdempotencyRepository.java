package com.ecommerce.repository;

import com.ecommerce.model.IdempotencyRecord;
import com.ecommerce.model.IdempotencyStatus;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class IdempotencyRepository {
    private final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

    public Optional<IdempotencyRecord> begin(String key, String fingerprint) {
        return Optional.ofNullable(records.putIfAbsent(key, IdempotencyRecord.inProgress(key, fingerprint)));
    }

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
