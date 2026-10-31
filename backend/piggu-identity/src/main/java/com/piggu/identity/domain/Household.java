package com.piggu.identity.domain;

import com.piggu.common.security.PigguRole;
import com.piggu.common.security.Plano;
import com.piggu.identity.billing.TipoDePlano;
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
 * casal, para o titular e o parceiro; no familia, para a casa inteira, que fica limitada a
 * 5 pessoas.</p>
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

    /** Primeira cobranca paga da assinatura: o arrependimento conta daqui, nao do teste. */
    @Column(name = "first_charge_at")
    private Instant firstChargeAt;

    /** INDIVIDUAL, CASAL ou FAMILIA ({@link TipoDePlano}). */
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
        this(UUID.randomUUID(), name);
    }

    public Household(UUID id, String name) {
        this.id = id;
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

    /**
     * Plano de uma pessoa da casa: no individual so quem assinou e Premium; no casal, quem
     * assinou e o casal (titular e parceiro); no familia, todos.
     */
    public Plano planoPara(UserAccount conta) {
        PigguRole papel = conta.getRole();
        return planoPara(conta.getId(),
                papel == PigguRole.TITULAR || papel == PigguRole.ADMIN || papel == PigguRole.PARCEIRO);
    }

    /** Sem saber o papel: no casal, so quem assinou. */
    public Plano planoPara(UUID contaId) {
        return planoPara(contaId, false);
    }

    /** @param doCasal a pessoa e titular ou parceiro da casa */
    public Plano planoPara(UUID contaId, boolean doCasal) {
        if (planoVigente() == Plano.GRATUITO) {
            return Plano.GRATUITO;
        }
        boolean coberta = switch (getTipo()) {
            case FAMILIA -> true;
            case CASAL -> doCasal || contaId.equals(subscriberId);
            case INDIVIDUAL -> contaId.equals(subscriberId);
        };
        return coberta ? Plano.PREMIUM : Plano.GRATUITO;
    }

    public boolean isFamilia() {
        return getTipo() == TipoDePlano.FAMILIA;
    }

    public boolean isCasal() {
        return getTipo() == TipoDePlano.CASAL;
    }

    public TipoDePlano getTipo() {
        try {
            return TipoDePlano.valueOf(planKind);
        } catch (IllegalArgumentException | NullPointerException desconhecido) {
            return TipoDePlano.INDIVIDUAL;
        }
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
     * @param tipo      quem da casa fica Premium
     * @param assinante conta que pagou
     */
    public boolean aplicarAssinatura(String origem, Instant ate, Instant momento, Instant inicio,
                                     TipoDePlano tipo, UUID assinante) {
        // Premium que vale so muda pela mesma origem e pelo mesmo assinante: o fim da assinatura de outro
        // lugar ou de outra pessoa nao derruba nem toma o plano de quem paga agora (D-078, D-143).
        if (planoVigente() == Plano.PREMIUM && (planSource != null && !planSource.equals(origem)
                || subscriberId != null && !subscriberId.equals(assinante))) {
            return false;
        }
        if (!aplicarAssinatura(origem, ate, momento, inicio)) {
            return false;
        }
        this.planKind = tipo.name();
        this.subscriberId = assinante;
        return true;
    }

    /** @param inicio quando a assinatura comecou; nulo mantem o que ja se sabia */
    public boolean aplicarAssinatura(String origem, Instant ate, Instant momento, Instant inicio) {
        if (billingEventAt != null && momento.isBefore(billingEventAt)) {
            return false;
        }
        // Dois eventos no mesmo segundo (a Stripe manda created e updated juntos): o empate nao encurta o Premium.
        if (momento.equals(billingEventAt) && premiumUntil != null && (ate == null || ate.isBefore(premiumUntil))) {
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

    /** Nulo mantem o que ja se sabia: o aviso de uma assinatura em teste nao apaga nada. */
    public void registrarPrimeiraCobranca(Instant quando) {
        if (quando != null) {
            this.firstChargeAt = quando;
        }
    }

    /** Primeira cobranca deste contrato; nula durante o teste (ou se for de um contrato anterior). */
    public Instant getPrimeiraCobranca() {
        return firstChargeAt != null && premiumSince != null && firstChargeAt.isBefore(premiumSince)
                ? null : firstChargeAt;
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
