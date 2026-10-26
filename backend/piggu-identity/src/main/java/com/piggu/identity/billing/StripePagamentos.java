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
 * Cobranca do Premium no site: Stripe Checkout + Portal do Cliente, pela API REST (sem SDK:
 * sao duas chamadas e um HMAC). Nenhum dado de cartao passa pelo Piggu: a pessoa paga numa
 * pagina da Stripe e o Piggu so fica sabendo pelo webhook.
 *
 * <p>O id do usuario vai em {@code subscription_data.metadata.userId}; e por ele que o
 * webhook sabe de quem e a assinatura. Vai o UUID, nao o e-mail.</p>
 */
@Component
public class StripePagamentos {

    private static final Logger log = LoggerFactory.getLogger(StripePagamentos.class);

    /** A Stripe recomenda recusar avisos com mais de 5 minutos: barra reenvio de aviso capturado. */
    private static final Duration TOLERANCIA = Duration.ofMinutes(5);

    /** trial_will_end chega 3 dias antes do fim do teste: e a hora do e-mail de aviso. */
    private static final Set<String> EVENTOS = Set.of("customer.subscription.created",
            "customer.subscription.updated", "customer.subscription.deleted", "customer.subscription.trial_will_end");

    /** past_due segue Premium enquanto a Stripe tenta cobrar de novo, mas so pela {@link #CARENCIA}. */
    private static final Set<String> STATUS_PAGOS = Set.of("active", "trialing", "past_due");

    /**
     * Em past_due a Stripe ja moveu o fim do periodo para o fim do periodo nao pago: no anual, um
     * cartao recusado daria mais um ano. A carencia conta do inicio desse periodo.
     */
    private static final Duration CARENCIA = Duration.ofDays(7);

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

    public boolean habilitado() {
        return propriedades.habilitado();
    }

    /** O plano familia tem preco configurado na Stripe. */
    public boolean familiaHabilitado() {
        return propriedades.familiaHabilitado();
    }

    /** O plano casal tem preco configurado na Stripe. */
    public boolean casalHabilitado() {
        return propriedades.casalHabilitado();
    }

    /**
     * Endereco da pagina de pagamento da Stripe.
     *
     * @param diasDeTeste dias gratis antes da primeira cobranca; 0 cobra na hora
     * @param campanha    campanha de teste gratis, devolvida no webhook para registrar o uso; pode ser nula
     */
    public String abrirCheckout(UserAccount conta, Periodo periodo, int diasDeTeste, String campanha) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        if (periodo.familia() && !propriedades.familiaHabilitado()) {
            throw new BusinessException("O plano família ainda não está disponível.");
        }
        if (periodo.casal() && !propriedades.casalHabilitado()) {
            throw new BusinessException("O plano casal ainda não está disponível.");
        }
        form.add("line_items[0][price]", propriedades.precoDe(periodo));
        form.add("line_items[0][quantity]", "1");
        form.add("client_reference_id", conta.getId().toString());
        form.add("subscription_data[metadata][userId]", conta.getId().toString());
        if (diasDeTeste > 0) {
            form.add("subscription_data[trial_period_days]", Integer.toString(diasDeTeste));
        }
        if (campanha != null) {
            form.add("subscription_data[metadata][campanha]", campanha);
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

    /** Endereco onde a pessoa troca cartao, ve recibos e cancela. */
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
     * Arrependimento (CDC art. 49): cancela na hora e devolve tudo o que foi cobrado nesta
     * assinatura, se a primeira cobranca paga tem menos de {@code prazo}. Acha a assinatura viva
     * do cliente e as cobrancas pagas dela (feitas desde o inicio que a propria Stripe guarda). O
     * prazo conta da primeira cobranca paga, nao do inicio: o teste gratis nao gasta os 7 dias.
     * Sem cobranca ainda (no teste), so cancela.
     *
     * @return se alguma cobranca foi devolvida; falso quando cancelou no teste
     * @throws BusinessException quando passou do prazo ou nao ha assinatura
     */
    public boolean cancelarComReembolso(String clienteNoProvedor, Duration prazo) {
        JsonNode assinatura = StreamSupport.stream(
                        obter("/v1/subscriptions?customer={c}&status=all&limit=10", clienteNoProvedor)
                                .path("data").spliterator(), false)
                .filter(item -> STATUS_PAGOS.contains(item.path("status").asString()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Não achei uma assinatura ativa pelo site."));

        long inicio = assinatura.path("start_date").asLong();
        List<JsonNode> pagas = StreamSupport.stream(
                        obter("/v1/charges?customer={c}&limit=20", clienteNoProvedor).path("data").spliterator(), false)
                .filter(cobranca -> cobranca.path("created").asLong() >= inicio && cobranca.path("paid").asBoolean())
                .toList();

        Optional<Instant> primeira = pagas.stream()
                .map(cobranca -> Instant.ofEpochSecond(cobranca.path("created").asLong()))
                .min(Instant::compareTo);
        if (primeira.isPresent() && Duration.between(primeira.get(), relogio.instant()).compareTo(prazo) > 0) {
            throw new BusinessException("Já se passaram os 7 dias desde a primeira cobrança. Você pode cancelar em "
                    + "Gerenciar assinatura, e o Premium segue até o fim do período pago.",
                    HttpStatus.CONFLICT, "PRAZO_DE_REEMBOLSO");
        }

        int devolvidas = 0;
        for (JsonNode cobranca : pagas) {
            if (!cobranca.path("refunded").asBoolean()) {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("charge", cobranca.path("id").asString());
                form.add("reason", "requested_by_customer");
                postar("/v1/refunds", form);
                devolvidas++;
            }
        }
        apagar("/v1/subscriptions/{id}", assinatura.path("id").asString());
        log.info("Assinatura cancelada no arrependimento: cobrancasDevolvidas={}", devolvidas);
        return devolvidas > 0;
    }

    /**
     * Apaga o cliente na Stripe, o que cancela na hora qualquer assinatura dele. Usado quando a
     * conta e excluida: ninguem continua pagando por uma conta que nao existe.
     */
    public void encerrarCliente(String clienteNoProvedor) {
        if (!habilitado()) {
            throw new UpstreamException(INDISPONIVEL);
        }
        try {
            cliente.delete().uri("/v1/customers/{id}", clienteNoProvedor)
                    .header("Authorization", "Bearer " + propriedades.secretKey())
                    .retrieve()
                    // Cliente que a Stripe ja nao conhece conta como encerrado. O 404 precisa do
                    // proprio tratamento: sem ele o padrao do RestClient lanca erro em todo 4xx.
                    .onStatus(status -> status.value() == 404, (req, res) -> { })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
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

    /**
     * Confere a assinatura do aviso e o traduz.
     *
     * @return vazio quando o aviso e valido mas nao muda a assinatura de ninguem
     * @throws UnauthorizedException quando a assinatura nao confere
     */
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
        // Ativa quer dizer que a primeira cobranca passou: no fim do teste, ou no inicio quando nao
        // houve teste. Em teste (ou outro status) fica nulo e a familia mantem o que ja sabia.
        long trialEnd = assinaturaStripe.path("trial_end").asLong(0);
        long primeiraCobranca = "active".equals(assinaturaStripe.path("status").asString())
                ? (trialEnd != 0 ? trialEnd : inicio) : 0;
        return Optional.of(new EventoDeAssinatura(
                UUID.fromString(usuario),
                assinaturaStripe.path("customer").asString(null),
                paga ? premiumAte(assinaturaStripe) : null,
                Instant.ofEpochSecond(evento.path("created").asLong()),
                inicio == 0 ? null : Instant.ofEpochSecond(inicio),
                "trialing".equals(assinaturaStripe.path("status").asString()),
                // O tipo sai do preco cobrado, nao do metadata: trocar de plano pelo portal
                // muda o preco e nao o metadata.
                propriedades.tipoDoPreco(
                        assinaturaStripe.path("items").path("data").path(0).path("price").path("id").asString("")),
                primeiraCobranca == 0 ? null : Instant.ofEpochSecond(primeiraCobranca),
                assinaturaStripe.path("metadata").path("campanha").asString(null),
                tipo.endsWith(".trial_will_end") && trialEnd != 0 ? Instant.ofEpochSecond(trialEnd) : null));
    }

    private static Instant premiumAte(JsonNode assinatura) {
        if ("past_due".equals(assinatura.path("status").asString())) {
            Instant inicio = doPeriodo(assinatura, "current_period_start");
            return inicio == null ? null : inicio.plus(CARENCIA);
        }
        return doPeriodo(assinatura, "current_period_end");
    }

    /** Nas versoes novas da API o periodo mora no item, nao na assinatura. */
    private static Instant doPeriodo(JsonNode assinatura, String campo) {
        long momento = assinatura.path(campo).asLong(0);
        if (momento == 0) {
            momento = assinatura.path("items").path("data").path(0).path(campo).asLong(0);
        }
        return momento == 0 ? null : Instant.ofEpochSecond(momento);
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
