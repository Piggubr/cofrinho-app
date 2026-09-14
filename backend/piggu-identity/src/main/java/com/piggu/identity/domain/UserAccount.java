package com.piggu.identity.domain;

import com.piggu.common.security.PigguRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Conta de usuario — o equivalente a uma linha da aba "Usuarios".
 *
 * <p>Os dados vindos do Google (nome, foto, primeiro nome) sao atualizados a cada login,
 * como fazia {@code obterOuCriarUsuario_}. O apelido, quando preenchido, tem preferencia
 * sobre o nome do Google na hora de exibir.</p>
 */
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PigguRole role;

    @Column(length = 120)
    private String nickname;

    @Column(name = "google_name", length = 120)
    private String googleName;

    @Column(name = "google_picture", length = 1000)
    private String googlePicture;

    @Column(name = "first_name", length = 80)
    private String firstName;

    @Column(nullable = false)
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> permissions = new HashMap<>();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccount() {
    }

    public UserAccount(String email, PigguRole role) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.role = role;
    }

    @PrePersist
    void aoCriar() {
        Instant agora = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = agora;
        updatedAt = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        updatedAt = Instant.now();
    }

    /** Grava os dados do perfil Google, com os mesmos limites de tamanho do Apps Script. */
    public void atualizarPerfilGoogle(String nome, String primeiroNome, String foto) {
        this.googleName = nome;
        this.firstName = primeiroNome;
        this.googlePicture = foto;
        this.lastLoginAt = Instant.now();
    }

    /** Nome de exibicao: apelido, senao nome do Google, senao o primeiro nome. */
    public String nomeExibicao() {
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        if (googleName != null && !googleName.isBlank()) {
            return googleName;
        }
        return primeiroNomeExibicao();
    }

    public String primeiroNomeExibicao() {
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        if (firstName != null && !firstName.isBlank()) {
            return firstName;
        }
        if (googleName != null && !googleName.isBlank()) {
            return googleName.split("\\s+")[0];
        }
        return email.split("@")[0];
    }

    public UUID getId() {
        return id;
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

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getGooglePicture() {
        return googlePicture;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Map<String, Object> getPermissions() {
        return permissions;
    }

    public void setPermissions(Map<String, Object> permissions) {
        this.permissions = permissions == null ? new HashMap<>() : permissions;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
