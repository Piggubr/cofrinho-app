package com.piggu.lifestyle.api.dto;

import com.piggu.lifestyle.domain.GirlsMathItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param fotoAssetId identificador da foto no servico de media; o front monta a URL
 *                    a partir dele
 */
public record GirlsMathResponse(
        UUID id,
        String produto,
        String loja,
        BigDecimal valor,
        LocalDate data,
        UUID fotoAssetId,
        boolean temFoto
) {

    public static GirlsMathResponse de(GirlsMathItem item) {
        return new GirlsMathResponse(
                item.getId(),
                item.getProduct(),
                item.getStore(),
                item.getAmount(),
                item.getItemDate(),
                item.getPhotoAssetId(),
                item.getPhotoAssetId() != null
        );
    }
}
