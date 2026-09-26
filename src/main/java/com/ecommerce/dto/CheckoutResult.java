package com.ecommerce.dto;

import com.ecommerce.model.Order;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CheckoutResult {
    private Order order;
    private boolean replayed;
}
