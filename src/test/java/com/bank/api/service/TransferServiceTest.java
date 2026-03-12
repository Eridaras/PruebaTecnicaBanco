package com.bank.api.service;

import com.bank.api.domain.entity.Account;
import com.bank.api.domain.entity.Movement;
import com.bank.api.domain.enums.AccountType;
import com.bank.api.dto.request.TransferRequest;
import com.bank.api.dto.response.TransferResponse;
import com.bank.api.exception.AccountNotFoundException;
import com.bank.api.exception.InsufficientFundsException;
import com.bank.api.exception.SameAccountTransferException;
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
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private MovementRepository movementRepository;

    @InjectMocks
    private TransferService transferService;

    private Account source;
    private Account target;

    @BeforeEach
    void setUp() {
        source = new Account("ACC-001", AccountType.AHORRO, new BigDecimal("1000.00"), null);
        target = new Account("ACC-002", AccountType.CORRIENTE, new BigDecimal("500.00"), null);
    }

    @Test
    void transfer_success() {
        when(accountRepository.findByAccountNumberWithLock("ACC-001")).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("ACC-002")).thenReturn(Optional.of(target));
        when(movementRepository.save(any(Movement.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferRequest request = new TransferRequest("ACC-001", "ACC-002", new BigDecimal("300.00"), "Pago");
        TransferResponse response = transferService.transfer(request);

        assertThat(response.sourceNewBalance()).isEqualByComparingTo("700.00");
        assertThat(response.targetNewBalance()).isEqualByComparingTo("800.00");
        verify(movementRepository, times(2)).save(any());
    }

    @Test
    void transfer_sameAccount_throws() {
        TransferRequest request = new TransferRequest("ACC-001", "ACC-001", new BigDecimal("100.00"), null);

        assertThatThrownBy(() -> transferService.transfer(request))
                .isInstanceOf(SameAccountTransferException.class);

        verifyNoInteractions(accountRepository);
    }

    @Test
    void transfer_insufficientFunds_throws() {
        when(accountRepository.findByAccountNumberWithLock("ACC-001")).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("ACC-002")).thenReturn(Optional.of(target));

        TransferRequest request = new TransferRequest("ACC-001", "ACC-002", new BigDecimal("9999.00"), null);

        assertThatThrownBy(() -> transferService.transfer(request))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("ACC-001");
    }

    @Test
    void transfer_sourceNotFound_throws() {
        when(accountRepository.findByAccountNumberWithLock("ACC-999")).thenReturn(Optional.empty());

        TransferRequest request = new TransferRequest("ACC-999", "ACC-002", new BigDecimal("100.00"), null);

        assertThatThrownBy(() -> transferService.transfer(request))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("ACC-999");
    }

    @Test
    void transfer_targetNotFound_throws() {
        when(accountRepository.findByAccountNumberWithLock("ACC-001")).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("ACC-999")).thenReturn(Optional.empty());

        TransferRequest request = new TransferRequest("ACC-001", "ACC-999", new BigDecimal("100.00"), null);

        assertThatThrownBy(() -> transferService.transfer(request))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("ACC-999");
    }
}
