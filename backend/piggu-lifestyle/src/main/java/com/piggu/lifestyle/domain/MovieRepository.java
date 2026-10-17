package com.piggu.lifestyle.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MovieRepository extends JpaRepository<Movie, UUID> {

    List<Movie> findAllByOrderByCreatedAtDesc();

    /** Por pessoa: o titular ve os filmes dos menores, mas tem a propria lista. */
    Optional<Movie> findByTmdbIdAndUserId(String tmdbId, UUID userId);

}
