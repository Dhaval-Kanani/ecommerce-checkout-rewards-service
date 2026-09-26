package com.ecommerce.repository;

import com.ecommerce.model.DiscountCode;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class DiscountCodeRepository {
    
    private final Map<String, DiscountCode> discountCodes = new ConcurrentHashMap<>();
    private DiscountCode currentActiveCode;
    
    public DiscountCode save(DiscountCode discountCode) {
        discountCodes.put(discountCode.getCode(), discountCode);
        if (!discountCode.isUsed()) {
            currentActiveCode = discountCode;
        }
        return discountCode;
    }
    
    public Optional<DiscountCode> findByCode(String code) {
        return Optional.ofNullable(discountCodes.get(code));
    }
    
    public List<DiscountCode> findAll() {
        return new ArrayList<>(discountCodes.values());
    }
    
    public Optional<DiscountCode> getCurrentActiveCode() {
        return Optional.ofNullable(currentActiveCode);
    }
    
    public void clearActiveCode() {
        currentActiveCode = null;
    }
}
