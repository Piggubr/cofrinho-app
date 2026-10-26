package com.piggu.identity.billing;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Assinatura comprada dentro do app (App Store / Google Play, que exigem a compra no app para
 * conteudo digital): avisos do RevenueCat pela API REST de webhooks, sem SDK.
 *
 * <p>O RevenueCat valida o recibo com a Apple e o Google e manda o resultado. A prova de
 * que o aviso veio dele e o cabecalho Authorization com o segredo combinado no painel
 * (REVENUECAT_WEBHOOK_SECRET), comparado em tempo constante. O app precisa registrar o
 * id da conta Piggu como app_user_id, senao o aviso nao tem dono e e ignorado.</p>
 */
@Component
public class RevenueCatLoja {

    /** EXPIRATION encerra; CANCELLATION so desliga a renovacao: o Premium vai ate expirar. */
    private static final Set<String> MUDAM_A_ASSINATURA = Set.of("INITIAL_PURCHASE", "RENEWAL", "UNCANCELLATION",
            "PRODUCT_CHANGE", "CANCELLATION", "BILLING_ISSUE", "SUBSCRIPTION_EXTENDED", "EXPIRATION");
    private static final String SEM_ASSINATURA = "Aviso da loja sem autenticacao valida.";

    /**
     * @param id     identificador unico do aviso, para processar cada um uma vez so
     * @param origem APP_STORE ou PLAY_STORE
     */
    public record AvisoDaLoja(String id, String origem, EventoDeAssinatura evento) {
    }

    private final String segredo;
    private final boolean aceitaSandbox;
    private final ObjectMapper json;

    public RevenueCatLoja(@Value("${piggu.revenuecat.webhook-secret:}") String segredo,
                          @Value("${piggu.ambiente:dev}") String ambiente,
                          ObjectMapper json) {
        this.segredo = segredo == null ? "" : segredo.trim();
        this.aceitaSandbox = !"producao".equals(ambiente);
        this.json = json;
    }

    public boolean habilitada() {
        return !segredo.isEmpty();
    }

    /**
     * Confere a autenticacao do aviso e o traduz.
     *
     * @param loja        a loja do caminho: APP_STORE ou PLAY_STORE
     * @param autorizacao o cabecalho Authorization que o intermediario mandou
     * @return vazio quando o aviso e valido mas nao muda a assinatura de ninguem
     * @throws UnauthorizedException quando a autenticacao nao confere
     */
    public Optional<AvisoDaLoja> lerAviso(String loja, String corpo, String autorizacao) {
        conferir(autorizacao);
        JsonNode evento;
        try {
            evento = json.readTree(corpo).path("event");
        } catch (RuntimeException ilegivel) {
            throw new BusinessException("Aviso da loja ilegivel.");
        }
        String tipo = evento.path("type").asString("");
        if (!MUDAM_A_ASSINATURA.contains(tipo)
                || (!aceitaSandbox && "SANDBOX".equals(evento.path("environment").asString("")))) {
            return Optional.empty();
        }
        if (!loja.equals(evento.path("store").asString(""))) {
            throw new BusinessException("O aviso e de outra loja.");
        }
        UUID conta;
        try {
            conta = UUID.fromString(evento.path("app_user_id").asString(""));
        } catch (IllegalArgumentException anonimo) {
            // Compra anonima ($RCAnonymousID): sem conta Piggu para receber o Premium.
            return Optional.empty();
        }

        long expira = evento.path("expiration_at_ms").asLong(0);
        Instant ate = "EXPIRATION".equals(tipo) || expira == 0 ? null : Instant.ofEpochMilli(expira);
        long comprou = evento.path("purchased_at_ms").asLong(0);
        return Optional.of(new AvisoDaLoja(
                evento.path("id").asString(""),
                loja,
                new EventoDeAssinatura(conta, null, ate,
                        Instant.ofEpochMilli(evento.path("event_timestamp_ms").asLong()),
                        comprou == 0 ? null : Instant.ofEpochMilli(comprou),
                        "TRIAL".equals(evento.path("period_type").asString("")),
                        tipoDoProduto(evento.path("product_id").asString("")), null, null, null)));
    }

    /** Produtos do familia e do casal nas lojas tem o plano no id (ex.: piggu_familia_mensal, piggu_casal_anual). */
    static TipoDePlano tipoDoProduto(String produto) {
        String id = produto.toLowerCase(java.util.Locale.ROOT);
        return id.contains("familia") ? TipoDePlano.FAMILIA
                : id.contains("casal") ? TipoDePlano.CASAL : TipoDePlano.INDIVIDUAL;
    }

    /** Aceita o segredo puro ou "Bearer segredo", como o painel do RevenueCat permite configurar. */
    private void conferir(String autorizacao) {
        if (!habilitada() || autorizacao == null) {
            throw new UnauthorizedException(SEM_ASSINATURA);
        }
        String recebido = autorizacao.startsWith("Bearer ") ? autorizacao.substring(7) : autorizacao;
        if (!MessageDigest.isEqual(recebido.trim().getBytes(StandardCharsets.UTF_8), segredo.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException(SEM_ASSINATURA);
        }
    }
}
