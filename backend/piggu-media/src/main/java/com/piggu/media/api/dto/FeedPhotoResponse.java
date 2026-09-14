package com.piggu.media.api.dto;

import com.piggu.media.domain.FeedPhoto;

import java.time.Instant;
import java.util.UUID;

/**
 * @param assetId identificador da imagem; o front monta a URL de download a partir dele
 */
public record FeedPhotoResponse(
        UUID id,
        String mesKey,
        UUID assetId,
        String legenda,
        String usuario,
        Instant criadoEm
) {

    public static FeedPhotoResponse de(FeedPhoto foto) {
        return new FeedPhotoResponse(
                foto.getId(),
                foto.getMonthKey(),
                foto.getAssetId(),
                foto.getCaption(),
                foto.getUserEmail(),
                foto.getCreatedAt()
        );
    }
}
