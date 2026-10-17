package com.piggu.finance.api.dto;

import com.piggu.finance.domain.ProductMemory;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Historico de preco de um produto, como o painel de mercado exibe. */
public record ProductResponse(
        String chave,
        String nome,
        String categoria,
        BigDecimal ultimo,
        BigDecimal menor,
        BigDecimal maior,
        BigDecimal media,
        int compras,
        LocalDate data,
        BigDecimal variacao
) {

    public static ProductResponse de(ProductMemory produto) {
        return new ProductResponse(
                produto.getProductKey(),
                produto.getName(),
                produto.getCategory(),
                produto.getLastPrice(),
                produto.getMinPrice(),
                produto.getMaxPrice(),
                produto.getAvgPrice(),
                produto.getPurchases(),
                produto.getLastPurchase(),
                produto.getLastVariation()
        );
    }
}
