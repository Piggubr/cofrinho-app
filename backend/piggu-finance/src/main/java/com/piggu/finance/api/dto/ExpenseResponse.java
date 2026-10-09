package com.piggu.finance.api.dto;

import com.piggu.finance.domain.Expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        LocalDate data,
        UUID reciboId,
        String estabelecimento,
        String item,
        String categoria,
        BigDecimal valor,
        String tipo,
        String origem,
        String usuario,
        Instant registradoEm,
        UUID contaId,
        Short parcela,
        Short parcelas,
        String moedaOriginal,
        BigDecimal valorOriginal
) {

    public static ExpenseResponse de(Expense gasto) {
        return new ExpenseResponse(
                gasto.getId(),
                gasto.getExpenseDate(),
                gasto.getReceiptId(),
                gasto.getMerchant(),
                gasto.getItem(),
                gasto.getCategory(),
                gasto.getAmount(),
                gasto.getKind(),
                gasto.getSource(),
                gasto.getUserEmail(),
                gasto.getCreatedAt(),
                gasto.getAccountId(),
                gasto.getInstallmentNumber(),
                gasto.getInstallmentCount(),
                gasto.getOriginalCurrency(),
                gasto.getOriginalAmount()
        );
    }
}
