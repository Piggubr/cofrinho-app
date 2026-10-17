package com.piggu.lifestyle.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlaceRepository extends JpaRepository<Place, UUID> {

    List<Place> findAllByOrderByVisitDateDesc();
}
