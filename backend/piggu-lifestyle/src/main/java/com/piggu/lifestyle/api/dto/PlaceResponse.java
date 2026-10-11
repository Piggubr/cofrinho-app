package com.piggu.lifestyle.api.dto;

import com.piggu.lifestyle.domain.Place;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param fotoAssetId identificador da foto no servico de media; o front monta a URL
 *                    a partir dele, em vez de receber a imagem inteira em base64
 */
public record PlaceResponse(
        UUID id,
        String nome,
        String categoria,
        String localizacao,
        int nota,
        String comentario,
        LocalDate data,
        List<String> marcacoes,
        UUID fotoAssetId,
        boolean temFoto,
        BigDecimal valor,
        UUID usuario
) {

    public static PlaceResponse de(Place lugar) {
        return new PlaceResponse(
                lugar.getId(),
                lugar.getName(),
                lugar.getCategory(),
                lugar.getLocation(),
                lugar.getRating(),
                lugar.getComment(),
                lugar.getVisitDate(),
                lugar.getTags(),
                lugar.getPhotoAssetId(),
                lugar.getPhotoAssetId() != null,
                lugar.getAmount(),
                lugar.getUserId()
        );
    }
}
