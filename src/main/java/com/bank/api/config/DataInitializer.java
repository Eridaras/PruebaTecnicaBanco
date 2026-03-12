package com.bank.api.config;

import com.bank.api.domain.entity.Account;
import com.bank.api.domain.entity.Customer;
import com.bank.api.domain.entity.Movement;
import com.bank.api.domain.enums.AccountType;
import com.bank.api.domain.enums.MovementType;
import com.bank.api.repository.AccountRepository;
import com.bank.api.repository.CustomerRepository;
import com.bank.api.repository.MovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final MovementRepository movementRepository;

    @Override
    public void run(String... args) {
        if (customerRepository.count() > 0) {
            log.info("Datos ya inicializados, saltando carga inicial.");
            return;
        }

        log.info("Cargando datos iniciales...");

        // Clientes
        Customer ana = customerRepository.save(new Customer("Ana García", "12345678"));
        Customer carlos = customerRepository.save(new Customer("Carlos Ruiz", "87654321"));

        // Cuentas de Ana
        Account anaAhorro = accountRepository.save(
                new Account("ACC-001", AccountType.AHORRO, new BigDecimal("2500.00"), ana));
        Account anaCorriente = accountRepository.save(
                new Account("ACC-002", AccountType.CORRIENTE, new BigDecimal("1000.00"), ana));

        // Cuenta de Carlos
        Account carlosAhorro = accountRepository.save(
                new Account("ACC-003", AccountType.AHORRO, new BigDecimal("5000.00"), carlos));

        // Movimientos históricos
        movementRepository.save(new Movement(
                MovementType.DEPOSITO, new BigDecimal("3000.00"),
                "Depósito inicial", anaAhorro, null));

        movementRepository.save(new Movement(
                MovementType.RETIRO, new BigDecimal("500.00"),
                "Retiro cajero", anaAhorro, null));

        movementRepository.save(new Movement(
                MovementType.DEPOSITO, new BigDecimal("1000.00"),
                "Depósito inicial", anaCorriente, null));

        movementRepository.save(new Movement(
                MovementType.DEPOSITO, new BigDecimal("5000.00"),
                "Depósito inicial", carlosAhorro, null));

        movementRepository.save(new Movement(
                MovementType.TRANSFERENCIA, new BigDecimal("200.00"),
                "Transferencia a Carlos", anaAhorro, carlosAhorro));
        movementRepository.save(new Movement(
                MovementType.TRANSFERENCIA, new BigDecimal("200.00"),
                "Transferencia recibida de Ana", carlosAhorro, anaAhorro));

        log.info("Datos iniciales cargados exitosamente.");
        log.info("Cuentas disponibles: ACC-001 (Ana, ahorro, $2500), ACC-002 (Ana, corriente, $1000), ACC-003 (Carlos, ahorro, $5000)");
    }
}
