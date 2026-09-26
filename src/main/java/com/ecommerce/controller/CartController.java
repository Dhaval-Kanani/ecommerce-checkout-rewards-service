package com.ecommerce.controller;

import com.ecommerce.dto.AddItemRequest;
import com.ecommerce.dto.ApiResponse;
import com.ecommerce.model.Cart;
import com.ecommerce.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {
    
    private final CartService cartService;
    
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<Cart>> addItemToCart(
            @RequestParam(required = false) String cartId,
            @Valid @RequestBody AddItemRequest request) {
        
        Cart cart = cartService.addItemToCart(cartId, request.getItemId(), request.getQuantity());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Item added to cart successfully", cart));
    }
    
    @GetMapping("/{cartId}")
    public ResponseEntity<ApiResponse<Cart>> getCart(@PathVariable String cartId) {
        Cart cart = cartService.getCart(cartId);
        return ResponseEntity.ok(ApiResponse.success(cart));
    }
    
    @DeleteMapping("/{cartId}/items/{itemId}")
    public ResponseEntity<ApiResponse<Cart>> removeItemFromCart(
            @PathVariable String cartId,
            @PathVariable String itemId) {
        
        Cart cart = cartService.removeItemFromCart(cartId, itemId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart", cart));
    }
}
