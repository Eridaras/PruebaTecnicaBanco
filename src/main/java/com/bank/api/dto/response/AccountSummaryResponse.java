package com.bank.api.dto.response;

import com.bank.api.domain.entity.Account;
import com.bank.api.domain.enums.AccountType;

import java.math.BigDecimal;

public record AccountSummaryResponse(
        Long id,
        String accountNumber,
        AccountType type,
        BigDecimal balance
) {
    public static AccountSummaryResponse from(Account account) {
        return new AccountSummaryResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getType(),
                account.getBalance()
        );
    }
}
