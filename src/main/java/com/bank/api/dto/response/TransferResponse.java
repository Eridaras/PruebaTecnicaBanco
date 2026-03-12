package com.bank.api.dto.response;

import java.math.BigDecimal;

public record TransferResponse(
        String sourceAccountNumber,
        BigDecimal sourceNewBalance,
        String targetAccountNumber,
        BigDecimal targetNewBalance,
        BigDecimal amount,
        String description
) {}
