package com.piggu.media.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeedPhotoRepository extends JpaRepository<FeedPhoto, UUID> {

    List<FeedPhoto> findAllByOrderByMonthKeyDescCreatedAtDesc();

    List<FeedPhoto> findByMonthKeyOrderByCreatedAtDesc(String monthKey);
}
