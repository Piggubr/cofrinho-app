package com.piggu.identity.billing;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.error.UpstreamException;
import com.piggu.identity.config.StripeProperties;
import com.piggu.identity.domain.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
import java.util.stream.StreamSupport;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
    public String abrirCheckout(UserAccount conta, Periodo periodo, int diasDeTeste) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("line_items[0][price]", periodo == Periodo.ANUAL ? propriedades.priceAnual() : propriedades.priceMensal());
        form.add("line_items[0][quantity]", "1");
        form.add("client_reference_id", conta.getId().toString());
        form.add("subscription_data[metadata][userId]", conta.getId().toString());
        if (diasDeTeste > 0) {
            form.add("subscription_data[trial_period_days]", Integer.toString(diasDeTeste));
        }
        form.add("locale", "pt-BR");
        form.add("success_url", propriedades.siteUrl() + "/plano?assinatura=ok");
        form.add("cancel_url", propriedades.siteUrl() + "/plano");
        if (conta.getStripeCustomerId() != null) {
            form.add("customer", conta.getStripeCustomerId());
        } else {
            // A Stripe precisa do e-mail para mandar recibo e aviso de cobranca.
            form.add("customer_email", conta.getEmail());
        }
        return postar("/v1/checkout/sessions", form).path("url").asString();
    }

    @Override
    public String abrirPortal(UserAccount conta) {
        if (conta.getStripeCustomerId() == null) {
            throw new BusinessException("Você ainda não tem assinatura pelo site.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("customer", conta.getStripeCustomerId());
        form.add("return_url", propriedades.siteUrl() + "/plano");
        return postar("/v1/billing_portal/sessions", form).path("url").asString();
    }

    /**
     * Acha a assinatura viva do cliente, confere o prazo pela data de inicio que a propria
     * Stripe guarda, devolve cada cobranca feita desde entao e cancela na hora.
     */
    @Override
    public void cancelarComReembolso(String clienteNoProvedor, Duration prazo) {
        JsonNode assinatura = StreamSupport.stream(
                        obter("/v1/subscriptions?customer={c}&status=all&limit=10", clienteNoProvedor)
                                .path("data").spliterator(), false)
                .filter(item -> STATUS_PAGOS.contains(item.path("status").asString()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Não achei uma assinatura ativa pelo site."));

        Instant inicio = Instant.ofEpochSecond(assinatura.path("start_date").asLong());
        if (Duration.between(inicio, relogio.instant()).compareTo(prazo) > 0) {
            throw new BusinessException("Já se passaram os 7 dias do arrependimento. Você pode cancelar em "
                    + "Gerenciar assinatura, e o Premium segue até o fim do período pago.",
                    HttpStatus.CONFLICT, "PRAZO_DE_REEMBOLSO");
        }

        int devolvidas = 0;
        for (JsonNode cobranca : obter("/v1/charges?customer={c}&limit=20", clienteNoProvedor).path("data")) {
            boolean desteContrato = cobranca.path("created").asLong() >= inicio.getEpochSecond();
            if (desteContrato && cobranca.path("paid").asBoolean() && !cobranca.path("refunded").asBoolean()) {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("charge", cobranca.path("id").asString());
                form.add("reason", "requested_by_customer");
                postar("/v1/refunds", form);
                devolvidas++;
            }
        }
        apagar("/v1/subscriptions/{id}", assinatura.path("id").asString());
        log.info("Assinatura cancelada no arrependimento: cobrancasDevolvidas={}", devolvidas);
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
        String tipo = evento.path("type").asString();
        JsonNode assinaturaStripe = evento.path("data").path("object");
        String usuario = assinaturaStripe.path("metadata").path("userId").asString("");
        if (!EVENTOS.contains(tipo) || usuario.isBlank()) {
            return Optional.empty();
        }

        boolean paga = !tipo.endsWith(".deleted") && STATUS_PAGOS.contains(assinaturaStripe.path("status").asString());
        long inicio = assinaturaStripe.path("start_date").asLong(0);
        return Optional.of(new EventoDeAssinatura(
                UUID.fromString(usuario),
                assinaturaStripe.path("customer").asString(null),
                paga ? fimDoPeriodo(assinaturaStripe) : null,
                Instant.ofEpochSecond(evento.path("created").asLong()),
                inicio == 0 ? null : Instant.ofEpochSecond(inicio),
                "trialing".equals(assinaturaStripe.path("status").asString())));
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

    private JsonNode obter(String caminho, Object... variaveis) {
        if (!habilitado()) {
            throw new BusinessException("A assinatura pelo site ainda não está disponível.");
        }
        try {
            return cliente.get().uri(caminho, variaveis)
                    .header("Authorization", "Bearer " + propriedades.secretKey())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new UpstreamException(INDISPONIVEL);
                    })
                    .body(JsonNode.class);
        } catch (BusinessException erro) {
            throw erro;
        } catch (RuntimeException erro) {
            log.error("Falha ao consultar a Stripe", erro);
            throw new UpstreamException(INDISPONIVEL);
        }
    }

    private void apagar(String caminho, Object... variaveis) {
        try {
            cliente.delete().uri(caminho, variaveis)
                    .header("Authorization", "Bearer " + propriedades.secretKey())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new UpstreamException(INDISPONIVEL);
                    })
                    .toBodilessEntity();
        } catch (BusinessException erro) {
            throw erro;
        } catch (RuntimeException erro) {
            log.error("Falha ao cancelar na Stripe", erro);
            throw new UpstreamException(INDISPONIVEL);
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
