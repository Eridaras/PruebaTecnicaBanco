package com.bank.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MovementControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getMovements_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/ACC-001/movements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getMovements_unknownAccount_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/ACC-999/movements"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void deposit_returnsCreated() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/ACC-001/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 500.00, "description": "Test deposit"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("DEPOSITO"));
    }

    @Test
    void deposit_negativeAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/ACC-001/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": -100.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void withdrawal_insufficientFunds_returns422() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/ACC-001/withdrawal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 999999.00, "description": "Retiro excesivo"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void transfer_success_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceAccountNumber": "ACC-001",
                                  "targetAccountNumber": "ACC-003",
                                  "amount": 100.00,
                                  "description": "Prueba transferencia"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sourceAccountNumber").value("ACC-001"));
    }

    @Test
    void transfer_sameAccount_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceAccountNumber": "ACC-001",
                                  "targetAccountNumber": "ACC-001",
                                  "amount": 100.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SAME_ACCOUNT_TRANSFER"));
    }

    @Test
    void customerFinancialSummary_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/customers/1/financial-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accounts").isArray());
    }
}
