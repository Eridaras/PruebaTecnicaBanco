package com.bank.api.service;

import com.bank.api.domain.entity.Account;
import com.bank.api.domain.entity.Movement;
import com.bank.api.domain.enums.MovementType;
import com.bank.api.dto.request.DepositRequest;
import com.bank.api.dto.request.WithdrawalRequest;
import com.bank.api.dto.response.MovementResponse;
import com.bank.api.exception.AccountNotFoundException;
import com.bank.api.exception.InsufficientFundsException;
import com.bank.api.repository.AccountRepository;
import com.bank.api.repository.MovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MovementService {

    private final AccountRepository accountRepository;
    private final MovementRepository movementRepository;

    @Transactional(readOnly = true)
    public List<MovementResponse> getMovements(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        return movementRepository.findByAccountIdOrderByCreatedAtDesc(account.getId())
                .stream()
                .map(MovementResponse::from)
                .toList();
    }

    @Transactional
    public MovementResponse deposit(String accountNumber, DepositRequest request) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        account.setBalance(account.getBalance().add(request.amount()));

        Movement movement = movementRepository.save(new Movement(
                MovementType.DEPOSITO,
                request.amount(),
                request.description(),
                account,
                null
        ));

        return MovementResponse.from(movement);
    }

    @Transactional
    public MovementResponse withdraw(String accountNumber, WithdrawalRequest request) {
        // Lock pesimista: evita que dos retiros simultáneos lleven el saldo a negativo
        Account account = accountRepository.findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(accountNumber, account.getBalance(), request.amount());
        }

        account.setBalance(account.getBalance().subtract(request.amount()));

        Movement movement = movementRepository.save(new Movement(
                MovementType.RETIRO,
                request.amount(),
                request.description(),
                account,
                null
        ));

        return MovementResponse.from(movement);
    }
}
