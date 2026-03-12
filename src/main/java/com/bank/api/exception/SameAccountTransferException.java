package com.bank.api.exception;

public class SameAccountTransferException extends RuntimeException {
    public SameAccountTransferException() {
        super("La cuenta origen y destino no pueden ser la misma");
    }
}
