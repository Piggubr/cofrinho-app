package com.piggu.finance.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomCategoryRepository extends JpaRepository<CustomCategory, UUID> {

    List<CustomCategory> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
