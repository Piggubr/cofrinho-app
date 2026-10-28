package com.piggu.identity.billing;

import com.google.api.services.androidpublisher.model.ExternalAccountIdentifiers;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseLineItem;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.identity.google.GoogleIdTokenVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Assinatura comprada no Android pelo Google Play Billing, conferida na Google Play Developer
 * API, sem intermediario (como a Apple em {@link AppStoreLoja}).
 *
 * <p>O app manda so o {@code purchaseToken}; o estado de verdade sempre vem do
 * {@code purchases.subscriptionsv2.get}. O dono da compra e o {@code obfuscatedExternalAccountId},
 * que o app preenche com o id da conta Piggu na hora de comprar.</p>
 *
 * <p>Os avisos em tempo real (RTDN) chegam pelo push do Pub/Sub com um token OIDC assinado pelo
 * Google; o conteudo do aviso so diz qual token consultar.</p>
 */
@Component
public class GooglePlayLoja {

    private static final Logger log = LoggerFactory.getLogger(GooglePlayLoja.class);

    static final Set<String> PRODUTOS = Set.of("premium_mensal", "premium_anual");
    /** CANCELED so desligou a renovacao: o Premium vai ate o expiryTime. ON_HOLD, PAUSED, PENDING e EXPIRED, nao. */
    private static final Set<String> DAO_PREMIUM = Set.of("SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
            "SUBSCRIPTION_STATE_CANCELED");
    private static final String SEM_PROVA = "Aviso do Google Play sem autenticação válida.";

    /**
     * @param vence     o expiryTime da assinatura, mesmo quando ela ja nao da Premium
     * @param confirmar o Google ainda espera o acknowledge
     */
    public record AssinaturaDoGoogle(EventoDeAssinatura evento, String produto, String token, Instant vence, boolean confirmar) {
    }

    private final GooglePlayApi api;
    private final GoogleIdTokenVerifier google;
    private final ObjectMapper json;
    private final String audiencia;
    private final String contaDoPush;

    public GooglePlayLoja(GooglePlayApi api, GoogleIdTokenVerifier google, ObjectMapper json,
                          @Value("${piggu.google-play.push-audiencia:}") String audiencia,
                          @Value("${piggu.google-play.push-email:}") String contaDoPush) {
        this.api = api;
        this.google = google;
        this.json = json;
        this.audiencia = audiencia == null ? "" : audiencia.trim();
        this.contaDoPush = contaDoPush == null ? "" : contaDoPush.trim();
    }

    /**
     * Compra que o app mandou logo depois de comprar (ou ao restaurar).
     *
     * @throws BusinessException quando o Google nao conhece o token, nao e o Premium ou nao esta ativa
     */
    public AssinaturaDoGoogle lerCompra(String token) {
        exigirConfigurada(api.configurada());
        SubscriptionPurchaseV2 compra = api.assinatura(token).orElseThrow(
                () -> new BusinessException("Não deu para confirmar a compra com o Google Play. Tente restaurar as compras."));
        AssinaturaDoGoogle assinatura = traduzir(compra, token)
                .orElseThrow(() -> new BusinessException("Esta compra não é uma assinatura do Piggu Premium."));
        if (assinatura.evento().premiumAte() == null) {
            throw new BusinessException("Esta assinatura do Google Play não está ativa. Se o pagamento estiver pendente, "
                    + "o Premium chega assim que o Google confirmar.");
        }
        return assinatura;
    }

    /**
     * Aviso em tempo real do Google Play, pelo push do Pub/Sub.
     *
     * @param autorizacao o cabecalho Authorization do push (Bearer com o token OIDC do Google)
     * @param dados       o {@code message.data} do push, em base64
     * @return vazio quando o aviso nao muda assinatura do Premium (teste, compra unica, token desconhecido)
     * @throws UnauthorizedException sem token do Google; {@link ForbiddenException} com token de outra origem
     */
    public Optional<AssinaturaDoGoogle> lerAviso(String autorizacao, String dados) {
        exigirConfigurada(api.configurada() && !audiencia.isEmpty() && !contaDoPush.isEmpty());
        conferirPush(autorizacao);
        JsonNode aviso;
        try {
            aviso = json.readTree(Base64.getDecoder().decode(dados));
        } catch (RuntimeException ilegivel) {
            throw new BusinessException("Aviso do Google Play ilegível.");
        }
        String token = aviso.path("subscriptionNotification").path("purchaseToken").asString("");
        JsonNode anulada = aviso.path("voidedPurchaseNotification");
        if (token.isEmpty() && anulada.path("productType").asInt(0) == 1) {
            // Reembolso de assinatura (PRODUCT_TYPE_SUBSCRIPTION) tambem chega como compra anulada.
            token = anulada.path("purchaseToken").asString("");
        }
        if (token.isEmpty()) {
            return Optional.empty();
        }
        Optional<SubscriptionPurchaseV2> compra = api.assinatura(token);
        if (compra.isEmpty()) {
            log.warn("Aviso do Google Play com token que o Google não conhece neste app");
            return Optional.empty();
        }
        return traduzir(compra.get(), token);
    }

    /** Falha aqui nao desfaz a compra: o proximo aviso ou a proxima chamada do app tenta de novo. */
    public void confirmar(AssinaturaDoGoogle compra) {
        if (!compra.confirmar()) {
            return;
        }
        try {
            api.confirmar(compra.produto(), compra.token());
        } catch (IOException | RuntimeException falha) {
            log.warn("Acknowledge da assinatura no Google Play falhou; tenta de novo no próximo aviso", falha);
        }
    }

    /** O estado e o de agora, lido no Google: por isso o momento e o instante da leitura. */
    static Optional<AssinaturaDoGoogle> traduzir(SubscriptionPurchaseV2 compra, String token) {
        List<SubscriptionPurchaseLineItem> itens = compra.getLineItems() == null ? List.of() : compra.getLineItems();
        Optional<SubscriptionPurchaseLineItem> premium = itens.stream()
                .filter(item -> PRODUTOS.contains(item.getProductId()))
                .max(Comparator.comparing(item -> instante(item.getExpiryTime()), Comparator.nullsFirst(Comparator.<Instant>naturalOrder())));
        if (premium.isEmpty()) {
            return Optional.empty();
        }
        SubscriptionPurchaseLineItem item = premium.get();
        Instant vence = instante(item.getExpiryTime());
        boolean daPremium = DAO_PREMIUM.contains(compra.getSubscriptionState());
        ExternalAccountIdentifiers ids = compra.getExternalAccountIdentifiers();
        EventoDeAssinatura evento = new EventoDeAssinatura(conta(ids == null ? null : ids.getObfuscatedExternalAccountId()), null,
                daPremium ? vence : null, Instant.now(), instante(compra.getStartTime()),
                item.getOfferPhase() != null && item.getOfferPhase().getFreeTrial() != null,
                RevenueCatLoja.tipoDoProduto(item.getProductId()), null, null, null);
        return Optional.of(new AssinaturaDoGoogle(evento, item.getProductId(), token, vence,
                daPremium && "ACKNOWLEDGEMENT_STATE_PENDING".equals(compra.getAcknowledgementState())));
    }

    /** Token OIDC do push: assinado pelo Google, para a nossa URL e pela conta de servico combinada. */
    private void conferirPush(String autorizacao) {
        if (autorizacao == null || !autorizacao.startsWith("Bearer ")) {
            throw new UnauthorizedException(SEM_PROVA);
        }
        Jwt token = google.verificarDoGoogle(autorizacao.substring(7).trim());
        boolean nosso = token.getAudience() != null && token.getAudience().contains(audiencia)
                && contaDoPush.equals(token.getClaimAsString("email"))
                && Boolean.TRUE.equals(token.getClaim("email_verified"));
        if (!nosso) {
            throw new ForbiddenException("Aviso do Google Play de outra origem.");
        }
    }

    private static void exigirConfigurada(boolean configurada) {
        if (!configurada) {
            throw new BusinessException("A assinatura pelo Google Play ainda não está configurada.",
                    HttpStatus.NOT_IMPLEMENTED, "EM_BREVE");
        }
    }

    private static UUID conta(String id) {
        try {
            return id == null ? null : UUID.fromString(id);
        } catch (IllegalArgumentException outroFormato) {
            return null;
        }
    }

    private static Instant instante(String rfc3339) {
        return rfc3339 == null ? null : Instant.parse(rfc3339);
    }
}
