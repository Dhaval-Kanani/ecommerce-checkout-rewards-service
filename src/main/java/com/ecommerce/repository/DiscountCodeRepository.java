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

/**
 * Coupon store. Every status transition runs inside {@code compute}, which holds
 * the map's lock for that key, so check-and-set is one indivisible step. That is
 * what stops two concurrent checkouts from both claiming one coupon.
 *
 * <p>There is deliberately no notion of a single "currently active" coupon. Any
 * coupon in {@link CouponStatus#ISSUED} is redeemable, so minting a new one does
 * not strand the ones already handed out.
 */
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

    /**
     * Moves a coupon from ISSUED to RESERVED, atomically.
     *
     * <p>Exactly one of a set of competing callers can succeed. The losers see
     * {@link CouponUnavailableException} rather than silently sharing a discount.
     *
     * @throws InvalidDiscountCodeException if the code does not exist
     * @throws CouponUnavailableException   if it is already reserved or redeemed
     */
    public DiscountCode reserve(String code) {
        DiscountCode[] reserved = new DiscountCode[1];
        discountCodes.compute(code, (key, existing) -> {
            if (existing == null) {
                return null;
            }
            if (existing.getStatus() != CouponStatus.ISSUED) {
                // Thrown from inside compute: the mapping is left unchanged.
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

    /** RESERVED to REDEEMED. Terminal, so the coupon can never be claimed again. */
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

    /**
     * RESERVED back to ISSUED, so a coupon survives a checkout that failed after
     * claiming it. A no-op for any other status, which keeps compensation safe to
     * run on paths where the reservation never happened.
     */
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
