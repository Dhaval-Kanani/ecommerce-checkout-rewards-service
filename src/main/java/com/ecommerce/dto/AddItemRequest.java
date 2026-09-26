package com.ecommerce.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddItemRequest {
    @NotBlank(message = "Item ID is required")
    private String itemId;
    
    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;
}
