package com.ecommerce.controller;

import com.ecommerce.dto.AdminStatsResponse;
import com.ecommerce.dto.ApiResponse;
import com.ecommerce.model.DiscountCode;
import com.ecommerce.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    
    private final AdminService adminService;
    
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminStatsResponse>> getStatistics() {
        AdminStatsResponse stats = adminService.getStatistics();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
    
    @GetMapping("/discount-codes")
    public ResponseEntity<ApiResponse<List<DiscountCode>>> getDiscountCodes() {
        List<DiscountCode> discountCodes = adminService.getAllDiscountCodes();
        return ResponseEntity.ok(ApiResponse.success(discountCodes));
    }
}
