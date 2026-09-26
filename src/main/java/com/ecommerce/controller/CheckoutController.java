package com.ecommerce.controller;

import com.ecommerce.dto.ApiResponse;
import com.ecommerce.dto.CheckoutRequest;
import com.ecommerce.dto.CheckoutResult;
import com.ecommerce.model.Order;
import com.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final OrderService orderService;

    /**
     * Places an order.
     *
     * <p>The {@code Idempotency-Key} header is required. Without it a client that
     * retries a timed-out request cannot be distinguished from one deliberately
     * placing a second order, which is exactly the double-charge this guards.
     *
     * <p>Answers 201 when this request placed the order, and 200 when it replays
     * an order an earlier request with the same key already placed.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Order>> checkout(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {

        CheckoutResult result = orderService.checkout(
                request.getCartId(), request.getDiscountCode(), idempotencyKey);

        return ResponseEntity
                .status(result.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.success(
                        result.isReplayed()
                                ? "Order already placed for this Idempotency-Key"
                                : "Order placed successfully",
                        result.getOrder()));
    }
}
