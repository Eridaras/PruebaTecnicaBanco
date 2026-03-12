package com.bank.api.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String accountNumber, BigDecimal balance, BigDecimal requested) {
        super(String.format(
                "Fondos insuficientes en la cuenta %s. Saldo disponible: %.2f, Monto solicitado: %.2f",
                accountNumber, balance, requested
        ));
    }
}
