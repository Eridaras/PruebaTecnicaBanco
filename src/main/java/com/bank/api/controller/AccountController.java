package com.bank.api.controller;

import com.bank.api.dto.response.ApiResponse;
import com.bank.api.dto.response.CustomerFinancialSummaryResponse;
import com.bank.api.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{customerId}/financial-summary")
    public ResponseEntity<ApiResponse<CustomerFinancialSummaryResponse>> getFinancialSummary(
            @PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.ok(accountService.getCustomerFinancialSummary(customerId)));
    }
}
