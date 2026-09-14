package com.piggu.media.api.dto;

import com.piggu.media.domain.Asset;

import java.time.Instant;
import java.util.UUID;

public record AssetResponse(UUID id, String contentType, long tamanho, String contexto, Instant criadoEm) {

    public static AssetResponse de(Asset asset) {
        return new AssetResponse(
                asset.getId(),
                asset.getContentType(),
                asset.getSizeBytes(),
                asset.getContext(),
                asset.getCreatedAt()
        );
    }
}
