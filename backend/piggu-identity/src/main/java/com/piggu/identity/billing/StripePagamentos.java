package com.piggu.identity.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.error.UpstreamException;
import com.piggu.identity.config.StripeProperties;
import com.piggu.identity.domain.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stripe Checkout + Portal do Cliente, pela API REST (sem SDK: sao duas chamadas e um HMAC).
 *
 * <p>O id do usuario vai em {@code subscription_data.metadata.userId}; e por ele que o
 * webhook sabe de quem e a assinatura. Vai o UUID, nao o e-mail.</p>
 */
@Component
public class StripePagamentos implements ProvedorDePagamento {

    private static final Logger log = LoggerFactory.getLogger(StripePagamentos.class);

    /** A Stripe recomenda recusar avisos com mais de 5 minutos: barra reenvio de aviso capturado. */
    private static final Duration TOLERANCIA = Duration.ofMinutes(5);

    private static final Set<String> EVENTOS = Set.of(
            "customer.subscription.created", "customer.subscription.updated", "customer.subscription.deleted");

    /** past_due segue Premium ate o fim do periodo pago enquanto a Stripe tenta cobrar de novo. */
    private static final Set<String> STATUS_PAGOS = Set.of("active", "trialing", "past_due");

    private static final String INDISPONIVEL = "O pagamento está indisponível agora. Tente de novo em instantes.";
    private static final String SEM_ASSINATURA = "Aviso de pagamento sem assinatura válida.";

    private final RestClient cliente;
    private final StripeProperties propriedades;
    private final ObjectMapper json;
    private final Clock relogio;

    @Autowired
    public StripePagamentos(RestClient.Builder builder, StripeProperties propriedades, ObjectMapper json) {
        this(builder, propriedades, json, Clock.systemUTC());
    }

    StripePagamentos(RestClient.Builder builder, StripeProperties propriedades, ObjectMapper json, Clock relogio) {
        this.cliente = builder.baseUrl(propriedades.baseUrl()).build();
        this.propriedades = propriedades;
        this.json = json;
        this.relogio = relogio;
    }

    @Override
    public boolean habilitado() {
        return propriedades.habilitado();
    }

    @Override
    public String abrirCheckout(UserAccount conta, Periodo periodo) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("line_items[0][price]", periodo == Periodo.ANUAL ? propriedades.priceAnual() : propriedades.priceMensal());
        form.add("line_items[0][quantity]", "1");
        form.add("client_reference_id", conta.getId().toString());
        form.add("subscription_data[metadata][userId]", conta.getId().toString());
        form.add("locale", "pt-BR");
        form.add("success_url", propriedades.siteUrl() + "/plano?assinatura=ok");
        form.add("cancel_url", propriedades.siteUrl() + "/plano");
        if (conta.getStripeCustomerId() != null) {
            form.add("customer", conta.getStripeCustomerId());
        } else {
            // A Stripe precisa do e-mail para mandar recibo e aviso de cobranca.
            form.add("customer_email", conta.getEmail());
        }
        return postar("/v1/checkout/sessions", form).path("url").asText();
    }

    @Override
    public String abrirPortal(UserAccount conta) {
        if (conta.getStripeCustomerId() == null) {
            throw new BusinessException("Você ainda não tem assinatura pelo site.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("customer", conta.getStripeCustomerId());
        form.add("return_url", propriedades.siteUrl() + "/plano");
        return postar("/v1/billing_portal/sessions", form).path("url").asText();
    }

    @Override
    public void encerrarCliente(String clienteNoProvedor) {
        if (!habilitado()) {
            throw new UpstreamException(INDISPONIVEL);
        }
        try {
            cliente.delete().uri("/v1/customers/{id}", clienteNoProvedor)
                    .header("Authorization", "Bearer " + propriedades.secretKey())
                    .retrieve()
                    // Cliente que a Stripe ja nao conhece conta como encerrado.
                    .onStatus(status -> status.isError() && status.value() != 404, (req, res) -> {
                        throw new UpstreamException(INDISPONIVEL);
                    })
                    .toBodilessEntity();
        } catch (UpstreamException erro) {
            throw erro;
        } catch (RuntimeException erro) {
            log.error("Falha ao encerrar cliente na Stripe", erro);
            throw new UpstreamException(INDISPONIVEL);
        }
    }

    @Override
    public Optional<EventoDeAssinatura> lerWebhook(String corpo, String assinatura) {
        conferirAssinatura(corpo, assinatura);
        JsonNode evento;
        try {
            evento = json.readTree(corpo);
        } catch (Exception erro) {
            throw new BusinessException("Aviso da Stripe ilegível.");
        }
        String tipo = evento.path("type").asText();
        JsonNode assinaturaStripe = evento.path("data").path("object");
        String usuario = assinaturaStripe.path("metadata").path("userId").asText("");
        if (!EVENTOS.contains(tipo) || usuario.isBlank()) {
            return Optional.empty();
        }

        boolean paga = !tipo.endsWith(".deleted") && STATUS_PAGOS.contains(assinaturaStripe.path("status").asText());
        return Optional.of(new EventoDeAssinatura(
                UUID.fromString(usuario),
                assinaturaStripe.path("customer").asText(null),
                paga ? fimDoPeriodo(assinaturaStripe) : null,
                Instant.ofEpochSecond(evento.path("created").asLong())));
    }

    /** Nas versoes novas da API o fim do periodo mora no item, nao na assinatura. */
    private static Instant fimDoPeriodo(JsonNode assinatura) {
        long fim = assinatura.path("current_period_end").asLong(0);
        if (fim == 0) {
            fim = assinatura.path("items").path("data").path(0).path("current_period_end").asLong(0);
        }
        return fim == 0 ? null : Instant.ofEpochSecond(fim);
    }

    /** Cabecalho {@code Stripe-Signature: t=...,v1=...}: HMAC-SHA256 de "t.corpo" com o segredo do webhook. */
    void conferirAssinatura(String corpo, String cabecalho) {
        if (!habilitado() || cabecalho == null) {
            throw new UnauthorizedException(SEM_ASSINATURA);
        }
        long momento = -1;
        List<String> assinaturas = new ArrayList<>();
        for (String parte : cabecalho.split(",")) {
            String[] chaveValor = parte.trim().split("=", 2);
            if (chaveValor.length != 2) {
                continue;
            }
            if (chaveValor[0].equals("t")) {
                try {
                    momento = Long.parseLong(chaveValor[1]);
                } catch (NumberFormatException ignorado) {
                    momento = -1;
                }
            } else if (chaveValor[0].equals("v1")) {
                assinaturas.add(chaveValor[1]);
            }
        }
        if (momento < 0 || Duration.between(Instant.ofEpochSecond(momento), relogio.instant()).abs()
                .compareTo(TOLERANCIA) > 0) {
            throw new UnauthorizedException(SEM_ASSINATURA);
        }
        byte[] esperada = HexFormat.of().formatHex(hmac(momento + "." + corpo)).getBytes(StandardCharsets.UTF_8);
        boolean confere = assinaturas.stream()
                .anyMatch(recebida -> MessageDigest.isEqual(esperada, recebida.getBytes(StandardCharsets.UTF_8)));
        if (!confere) {
            throw new UnauthorizedException(SEM_ASSINATURA);
        }
    }

    String hmacHex(String conteudo) {
        return HexFormat.of().formatHex(hmac(conteudo));
    }

    private byte[] hmac(String conteudo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(propriedades.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(conteudo.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("HmacSHA256 indisponivel nesta JVM.", erro);
        }
    }

    private JsonNode postar(String caminho, MultiValueMap<String, String> form) {
        if (!habilitado()) {
            throw new BusinessException("A assinatura pelo site ainda não está disponível.");
        }
        try {
            return cliente.post().uri(caminho)
                    .header("Authorization", "Bearer " + propriedades.secretKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new UpstreamException(INDISPONIVEL);
                    })
                    .body(JsonNode.class);
        } catch (BusinessException erro) {
            throw erro;
        } catch (RuntimeException erro) {
            log.error("Falha ao falar com a Stripe em {}", caminho, erro);
            throw new UpstreamException(INDISPONIVEL);
        }
    }
}
