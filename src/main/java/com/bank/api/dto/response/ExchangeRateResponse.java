package com.bank.api.dto.response;

import java.math.BigDecimal;

public record ExchangeRateResponse(
        String fromCurrency,
        String toCurrency,
        BigDecimal originalAmount,
        BigDecimal convertedAmount,
        BigDecimal rate
) {}
