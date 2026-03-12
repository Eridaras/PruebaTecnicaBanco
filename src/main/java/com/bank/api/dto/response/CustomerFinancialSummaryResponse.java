package com.bank.api.dto.response;

import com.bank.api.domain.entity.Customer;

import java.util.List;

public record CustomerFinancialSummaryResponse(
        Long customerId,
        String name,
        String documentId,
        List<AccountSummaryResponse> accounts
) {
    public static CustomerFinancialSummaryResponse from(Customer customer, List<AccountSummaryResponse> accounts) {
        return new CustomerFinancialSummaryResponse(
                customer.getId(),
                customer.getName(),
                customer.getDocumentId(),
                accounts
        );
    }
}
