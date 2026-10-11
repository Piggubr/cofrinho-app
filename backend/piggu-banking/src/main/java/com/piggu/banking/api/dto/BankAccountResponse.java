package com.piggu.banking.api.dto;

import com.piggu.banking.domain.BankAccount;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** @param numero so os 4 ultimos digitos, ja mascarados ("•••• 1234"), ou vazio */
public record BankAccountResponse(
        UUID id,
        UUID conexaoId,
        String instituicao,
        String nome,
        String tipo,
        String numero,
        BigDecimal saldo,
        String moeda,
        String status,
        Instant atualizadoEm
) {

    public static BankAccountResponse de(BankAccount conta) {
        return new BankAccountResponse(
                conta.getId(),
                conta.getConnection().getId(),
                conta.getConnection().getInstitution(),
                conta.getName(),
                conta.getType(),
                conta.getNumber().isEmpty() ? "" : "•••• " + conta.getNumber(),
                conta.getBalance(),
                conta.getCurrency(),
                conta.getConnection().getStatus(),
                conta.getUpdatedAt()
        );
    }
}
