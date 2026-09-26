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
