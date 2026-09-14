package com.piggu.lifestyle.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MovieRepository extends JpaRepository<Movie, UUID> {

    List<Movie> findAllByOrderByCreatedAtDesc();

    Optional<Movie> findByTmdbId(String tmdbId);
}
