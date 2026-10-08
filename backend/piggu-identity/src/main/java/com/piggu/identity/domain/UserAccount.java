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

    /** Desde quando a conta esta desativada; a retencao apaga depois de um prazo. */
    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> permissions = new HashMap<>();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(name = "conversion_currency", nullable = false, length = 3)
    private String conversionCurrency = "BRL";

    @Column(name = "show_exchange_rate", nullable = false)
    private boolean showExchangeRate = true;

    /** Familia a que a conta pertence; o plano e os dados de dominio sao dela. */
    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    /** Cliente no provedor de pagamento de quem assinou, para abrir o portal depois. */
    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccount() {
    }

    public UserAccount(String email, PigguRole role, UUID householdId) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.role = role;
        this.householdId = householdId;
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
        if (this.active && !active) {
            this.deactivatedAt = Instant.now();
        } else if (active) {
            this.deactivatedAt = null;
        }
        this.active = active;
    }

    public Map<String, Object> getPermissions() {
        return permissions;
    }

    public void setPermissions(Map<String, Object> permissions) {
        this.permissions = permissions == null ? new HashMap<>() : permissions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    /** Os codigos chegam ja validados como ISO 4217. */
    public void alterarPreferencias(String currency, String conversionCurrency, boolean showExchangeRate) {
        this.currency = currency;
        this.conversionCurrency = conversionCurrency;
        this.showExchangeRate = showExchangeRate;
    }

    public String getCurrency() {
        return currency;
    }

    public String getConversionCurrency() {
        return conversionCurrency;
    }

    public boolean isShowExchangeRate() {
        return showExchangeRate;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    /** Sai de uma familia e entra em outra (convite aceito, membro removido). */
    public void mudarDeFamilia(UUID householdId, PigguRole role) {
        this.householdId = householdId;
        this.role = role;
    }

    /** Aceite dos termos e do aviso de privacidade, com a versao do texto. */
    public void aceitarTermos(String versao) {
        this.termsVersion = versao;
        this.termsAcceptedAt = Instant.now();
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public Instant getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public void lembrarClienteNoProvedor(String clienteNoProvedor) {
        if (clienteNoProvedor != null) {
            this.stripeCustomerId = clienteNoProvedor;
        }
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }
}
