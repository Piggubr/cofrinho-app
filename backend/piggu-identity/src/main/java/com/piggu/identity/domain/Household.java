package com.piggu.identity.domain;

import com.piggu.common.security.Plano;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Familia: o titular, os membros e tudo o que eles lancam.
 *
 * <p>O Premium mora aqui, e nao na conta: o titular assina e a familia inteira usa.</p>
 */
@Entity
@Table(name = "households")
public class Household {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name = "";

    @Column(name = "premium_until")
    private Instant premiumUntil;

    @Column(name = "plan_source", length = 20)
    private String planSource;

    @Column(name = "billing_event_at")
    private Instant billingEventAt;

    @Column(name = "premium_since")
    private Instant premiumSince;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Household() {
    }

    public Household(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
    }

    @PrePersist
    void aoCriar() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void aoAtualizar() {
        updatedAt = Instant.now();
    }

    /** Premium enquanto a data paga nao passou: vencer nao precisa de job. */
    public Plano planoVigente() {
        return premiumUntil != null && premiumUntil.isAfter(Instant.now()) ? Plano.PREMIUM : Plano.GRATUITO;
    }

    /**
     * Aplica o que o provedor de pagamento disse sobre a assinatura.
     *
     * @param ate     ate quando o Premium vale; no passado ou nulo encerra
     * @param momento quando o provedor gerou o evento
     * @return falso quando o evento e mais antigo que o ultimo aplicado (chegou fora de ordem)
     */
    public boolean aplicarAssinatura(String origem, Instant ate, Instant momento) {
        return aplicarAssinatura(origem, ate, momento, null);
    }

    /** @param inicio quando a assinatura comecou; nulo mantem o que ja se sabia */
    public boolean aplicarAssinatura(String origem, Instant ate, Instant momento, Instant inicio) {
        if (billingEventAt != null && momento.isBefore(billingEventAt)) {
            return false;
        }
        this.premiumUntil = ate;
        this.planSource = origem;
        this.billingEventAt = momento;
        if (inicio != null) {
            this.premiumSince = inicio;
        }
        return true;
    }

    public Instant getPremiumSince() {
        return premiumSince;
    }

    public void renomear(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getPremiumUntil() {
        return premiumUntil;
    }

    public String getPlanSource() {
        return planSource;
    }
}
