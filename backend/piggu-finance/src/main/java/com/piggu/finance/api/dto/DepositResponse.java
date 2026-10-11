package com.piggu.finance.api.dto;

import com.piggu.finance.domain.PiggyDeposit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DepositResponse(UUID id, LocalDate data, BigDecimal valor, UUID usuario, Instant registradoEm) {

    public static DepositResponse de(PiggyDeposit deposito) {
        return new DepositResponse(
                deposito.getId(),
                deposito.getDepositDate(),
                deposito.getAmount(),
                deposito.getUserId(),
                deposito.getCreatedAt()
        );
    }
}
