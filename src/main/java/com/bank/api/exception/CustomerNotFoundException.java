package com.bank.api.exception;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(Long id) {
        super("Cliente no encontrado con ID: " + id);
    }
}
