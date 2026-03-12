package com.bank.api.controller;

import com.bank.api.dto.request.DepositRequest;
import com.bank.api.dto.request.WithdrawalRequest;
import com.bank.api.dto.response.ApiResponse;
import com.bank.api.dto.response.MovementResponse;
import com.bank.api.service.MovementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts/{accountNumber}")
@RequiredArgsConstructor
public class MovementController {

    private final MovementService movementService;

    @GetMapping("/movements")
    public ResponseEntity<ApiResponse<List<MovementResponse>>> getMovements(
            @PathVariable String accountNumber) {
        return ResponseEntity.ok(ApiResponse.ok(movementService.getMovements(accountNumber)));
    }

    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<MovementResponse>> deposit(
            @PathVariable String accountNumber,
            @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(movementService.deposit(accountNumber, request)));
    }

    @PostMapping("/withdrawal")
    public ResponseEntity<ApiResponse<MovementResponse>> withdraw(
            @PathVariable String accountNumber,
            @Valid @RequestBody WithdrawalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(movementService.withdraw(accountNumber, request)));
    }
}
