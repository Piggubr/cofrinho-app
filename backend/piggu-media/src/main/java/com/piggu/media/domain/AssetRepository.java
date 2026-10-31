package com.piggu.media.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    /** Bytes guardados pela familia toda, com as fotos privadas: SQL puro, sem o filtro do dono. */
    @Query(value = "SELECT COALESCE(SUM(size_bytes), 0) FROM assets WHERE household_id = ?1", nativeQuery = true)
    long bytesDaFamilia(UUID familia);
}
