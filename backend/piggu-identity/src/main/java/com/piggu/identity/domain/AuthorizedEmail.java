package com.piggu.identity.domain;

import com.piggu.common.security.PigguRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * E-mail liberado para entrar, com o papel que recebe no primeiro acesso.
 *
 * <p>Equivale as constantes {@code EMAILS_AUTORIZADOS}, {@code EMAILS_ADMIN} e
 * {@code EMAIL_BEATRIZ} do Code.gs, so que agora editaveis em tempo de execucao.</p>
 */
@Entity
@Table(name = "authorized_emails")
public class AuthorizedEmail {

    @Id
    @Column(length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PigguRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AuthorizedEmail() {
    }

    public AuthorizedEmail(String email, PigguRole role) {
        this.email = email;
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public PigguRole getRole() {
        return role;
    }

    public void setRole(PigguRole role) {
        this.role = role;
    }
}
