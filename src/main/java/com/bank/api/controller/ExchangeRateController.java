package com.bank.api.controller;

import com.bank.api.dto.response.ApiResponse;
import com.bank.api.dto.response.ExchangeRateResponse;
import com.bank.api.service.ExchangeRateService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/exchange-rate")
@RequiredArgsConstructor
@Validated
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping
    public ResponseEntity<ApiResponse<ExchangeRateResponse>> convert(
            @RequestParam @NotBlank(message = "La moneda origen es obligatoria") String from,
            @RequestParam @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero") BigDecimal amount) {
        return ResponseEntity.ok(ApiResponse.ok(exchangeRateService.convert(from, amount)));
    }
}
