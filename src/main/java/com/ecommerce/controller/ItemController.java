package com.ecommerce.controller;

import com.ecommerce.dto.ApiResponse;
import com.ecommerce.model.Item;
import com.ecommerce.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {
    private final InventoryService inventoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Item>>> getAllItems() {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.catalogue()));
    }
}
