package com.bank.api.service;

import com.bank.api.domain.entity.Account;
import com.bank.api.domain.entity.Movement;
import com.bank.api.domain.enums.MovementType;
import com.bank.api.dto.request.TransferRequest;
import com.bank.api.dto.response.TransferResponse;
import com.bank.api.exception.AccountNotFoundException;
import com.bank.api.exception.InsufficientFundsException;
import com.bank.api.exception.SameAccountTransferException;
import com.bank.api.repository.AccountRepository;
import com.bank.api.repository.MovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountRepository accountRepository;
    private final MovementRepository movementRepository;

    /**
     * Realiza una transferencia entre dos cuentas de forma atómica.
     *
     * Se usa REPEATABLE_READ para que el saldo leído en la validación no cambie
     * antes de la actualización dentro de la misma transacción.
     * El lock pesimista en la cuenta origen previene condiciones de carrera
     * cuando múltiples transferencias se ejecutan concurrentemente desde la misma cuenta.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransferResponse transfer(TransferRequest request) {
        if (request.sourceAccountNumber().equals(request.targetAccountNumber())) {
            throw new SameAccountTransferException();
        }

        // Lock pesimista en cuenta origen para serializar operaciones concurrentes
        Account source = accountRepository.findByAccountNumberWithLock(request.sourceAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(request.sourceAccountNumber()));

        Account target = accountRepository.findByAccountNumber(request.targetAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(request.targetAccountNumber()));

        if (source.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(
                    request.sourceAccountNumber(), source.getBalance(), request.amount());
        }

        source.setBalance(source.getBalance().subtract(request.amount()));
        target.setBalance(target.getBalance().add(request.amount()));

        String description = request.description() != null ? request.description() : "Transferencia";

        // Trazabilidad en ambas cuentas mediante related_account_id
        movementRepository.save(new Movement(
                MovementType.TRANSFERENCIA, request.amount(),
                description + " → " + target.getAccountNumber(),
                source, target));

        movementRepository.save(new Movement(
                MovementType.TRANSFERENCIA, request.amount(),
                description + " ← " + source.getAccountNumber(),
                target, source));

        return new TransferResponse(
                source.getAccountNumber(), source.getBalance(),
                target.getAccountNumber(), target.getBalance(),
                request.amount(), description
        );
    }
}
