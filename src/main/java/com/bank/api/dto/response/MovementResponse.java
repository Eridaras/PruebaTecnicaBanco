package com.bank.api.dto.response;

import com.bank.api.domain.entity.Movement;
import com.bank.api.domain.enums.MovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovementResponse(
        Long id,
        MovementType type,
        BigDecimal amount,
        String description,
        LocalDateTime createdAt,
        String relatedAccountNumber
) {
    public static MovementResponse from(Movement movement) {
        String relatedAccount = movement.getRelatedAccount() != null
                ? movement.getRelatedAccount().getAccountNumber()
                : null;
        return new MovementResponse(
                movement.getId(),
                movement.getType(),
                movement.getAmount(),
                movement.getDescription(),
                movement.getCreatedAt(),
                relatedAccount
        );
    }
}
