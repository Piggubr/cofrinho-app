package com.piggu.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorizedEmailRepository extends JpaRepository<AuthorizedEmail, String> {

    Optional<AuthorizedEmail> findByEmail(String email);
}
