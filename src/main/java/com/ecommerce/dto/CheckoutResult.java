package com.ecommerce.dto;

import com.ecommerce.model.Order;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Outcome of a checkout call.
 *
 * <p>{@code replayed} distinguishes an order placed by this request from one an
 * earlier request with the same idempotency key already placed. The controller
 * uses it to answer 201 or 200.
 */
@Data
@AllArgsConstructor
public class CheckoutResult {
    private Order order;
    private boolean replayed;
}
