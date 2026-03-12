package com.bank.api.service;

import com.bank.api.domain.entity.Account;
import com.bank.api.dto.response.AccountSummaryResponse;
import com.bank.api.dto.response.CustomerFinancialSummaryResponse;
import com.bank.api.exception.CustomerNotFoundException;
import com.bank.api.repository.AccountRepository;
import com.bank.api.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public CustomerFinancialSummaryResponse getCustomerFinancialSummary(Long customerId) {
        var customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        List<AccountSummaryResponse> accounts = accountRepository.findByCustomerId(customerId)
                .stream()
                .map(AccountSummaryResponse::from)
                .toList();

        return CustomerFinancialSummaryResponse.from(customer, accounts);
    }
}
