package com.ecommerce.repository;

import com.ecommerce.exception.CouponUnavailableException;
import com.ecommerce.exception.InvalidDiscountCodeException;
import com.ecommerce.model.CouponStatus;
import com.ecommerce.model.DiscountCode;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class DiscountCodeRepository {
    private final Map<String, DiscountCode> discountCodes = new ConcurrentHashMap<>();

    public DiscountCode save(DiscountCode discountCode) {
        discountCodes.put(discountCode.getCode(), discountCode);
        return discountCode;
    }

    public Optional<DiscountCode> findByCode(String code) {
        return Optional.ofNullable(discountCodes.get(code));
    }

    public List<DiscountCode> findAll() {
        List<DiscountCode> all = new ArrayList<>(discountCodes.values());
        all.sort(Comparator.comparingInt(DiscountCode::getOrderNumber));
        return all;
    }

    public DiscountCode reserve(String code) {
        DiscountCode[] reserved = new DiscountCode[1];
        discountCodes.compute(code, (key, existing) -> {
            if (existing == null) {
                return null;
            }
            if (existing.getStatus() != CouponStatus.ISSUED) {
                throw new CouponUnavailableException(existing.getStatus() == CouponStatus.REDEEMED
                        ? "Discount code has already been redeemed: " + code
                        : "Discount code is being redeemed by another checkout: " + code);
            }
            existing.setStatus(CouponStatus.RESERVED);
            reserved[0] = existing;
            return existing;
        });
        if (reserved[0] == null) {
            throw new InvalidDiscountCodeException("Invalid discount code: " + code);
        }
        return reserved[0];
    }

    public void markRedeemed(String code) {
        discountCodes.compute(code, (key, existing) -> {
            if (existing == null) {
                throw new InvalidDiscountCodeException("Invalid discount code: " + code);
            }
            if (existing.getStatus() != CouponStatus.RESERVED) {
                throw new IllegalStateException(
                        "Cannot redeem coupon " + code + " from status " + existing.getStatus());
            }
            existing.setStatus(CouponStatus.REDEEMED);
            existing.setRedeemedAt(LocalDateTime.now());
            return existing;
        });
    }

    public void release(String code) {
        discountCodes.compute(code, (key, existing) -> {
            if (existing != null && existing.getStatus() == CouponStatus.RESERVED) {
                existing.setStatus(CouponStatus.ISSUED);
            }
            return existing;
        });
    }

    public long countByStatus(CouponStatus status) {
        return discountCodes.values().stream().filter(c -> c.getStatus() == status).count();
    }
}
