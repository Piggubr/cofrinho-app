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
 * <p>A assinatura mora aqui. No plano individual o Premium vale so para quem assinou; no
 * plano familia vale para a casa inteira, que fica limitada a 5 pessoas.</p>
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

    /** INDIVIDUAL ou FAMILIA. */
    @Column(name = "plan_kind", nullable = false, length = 20)
    private String planKind = "INDIVIDUAL";

    /** Conta que assinou: no individual, a unica com Premium. */
    @Column(name = "subscriber_id")
    private UUID subscriberId;

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

    /** Plano de uma pessoa da casa: no individual so quem assinou e Premium. */
    public Plano planoPara(UUID contaId) {
        if (planoVigente() == Plano.GRATUITO) {
            return Plano.GRATUITO;
        }
        return isFamilia() || contaId.equals(subscriberId) ? Plano.PREMIUM : Plano.GRATUITO;
    }

    public boolean isFamilia() {
        return "FAMILIA".equals(planKind);
    }

    public UUID getSubscriberId() {
        return subscriberId;
    }

    /** Premium da casa enquanto a data paga nao passou: vencer nao precisa de job. */
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

    /**
     * Assinatura com o tipo do plano e quem assinou.
     *
     * @param familia   plano familia (a casa toda) ou individual (so {@code assinante})
     * @param assinante conta que pagou
     */
    public boolean aplicarAssinatura(String origem, Instant ate, Instant momento, Instant inicio,
                                     boolean familia, UUID assinante) {
        if (!aplicarAssinatura(origem, ate, momento, inicio)) {
            return false;
        }
        this.planKind = familia ? "FAMILIA" : "INDIVIDUAL";
        this.subscriberId = assinante;
        return true;
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
