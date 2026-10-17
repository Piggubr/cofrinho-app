package com.piggu.lifestyle.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, UUID> {

    List<ShoppingItem> findAllByOrderByCreatedAtDesc();

    List<ShoppingItem> findByListNameOrderByCreatedAtDesc(String listName);
}
