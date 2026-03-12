package com.bank.api.service;

import com.bank.api.domain.entity.Account;
import com.bank.api.domain.entity.Movement;
import com.bank.api.domain.enums.AccountType;
import com.bank.api.dto.request.DepositRequest;
import com.bank.api.dto.request.WithdrawalRequest;
import com.bank.api.exception.AccountNotFoundException;
import com.bank.api.exception.InsufficientFundsException;
import com.bank.api.repository.AccountRepository;
import com.bank.api.repository.MovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovementServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private MovementRepository movementRepository;

    @InjectMocks
    private MovementService movementService;

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account("ACC-001", AccountType.AHORRO, new BigDecimal("1000.00"), null);
    }

    @Test
    void deposit_updatesBalance() {
        when(accountRepository.findByAccountNumber("ACC-001")).thenReturn(Optional.of(account));
        when(movementRepository.save(any(Movement.class))).thenAnswer(inv -> inv.getArgument(0));

        movementService.deposit("ACC-001", new DepositRequest(new BigDecimal("500.00"), "Test"));

        assertThat(account.getBalance()).isEqualByComparingTo("1500.00");
    }

    @Test
    void deposit_accountNotFound_throws() {
        when(accountRepository.findByAccountNumber("ACC-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> movementService.deposit("ACC-999", new DepositRequest(new BigDecimal("100.00"), null)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void withdraw_updatesBalance() {
        when(accountRepository.findByAccountNumberWithLock("ACC-001")).thenReturn(Optional.of(account));
        when(movementRepository.save(any(Movement.class))).thenAnswer(inv -> inv.getArgument(0));

        movementService.withdraw("ACC-001", new WithdrawalRequest(new BigDecimal("300.00"), "Test"));

        assertThat(account.getBalance()).isEqualByComparingTo("700.00");
    }

    @Test
    void withdraw_insufficientFunds_throws() {
        when(accountRepository.findByAccountNumberWithLock("ACC-001")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> movementService.withdraw("ACC-001", new WithdrawalRequest(new BigDecimal("2000.00"), null)))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void withdraw_exactBalance_succeeds() {
        when(accountRepository.findByAccountNumberWithLock("ACC-001")).thenReturn(Optional.of(account));
        when(movementRepository.save(any(Movement.class))).thenAnswer(inv -> inv.getArgument(0));

        movementService.withdraw("ACC-001", new WithdrawalRequest(new BigDecimal("1000.00"), "Retiro total"));

        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
    }
}
