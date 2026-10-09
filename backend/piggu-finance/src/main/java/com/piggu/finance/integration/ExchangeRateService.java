package com.piggu.finance.integration;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UpstreamException;
import com.piggu.common.web.Moedas;
import com.piggu.finance.api.dto.CurrencyResponse;
import com.piggu.finance.api.dto.ExchangeRateResponse;
import com.piggu.finance.config.IntegracoesProperties;
import com.piggu.finance.domain.AppSetting;
import com.piggu.finance.domain.AppSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * Cotacao de referencia entre duas moedas quaisquer que a fonte conheca.
 *
 * <p>Porte de carregarCotacaoReferencia_, que so fazia EUR para BRL, com a mesma
 * escada de tres degraus que o original tinha, e que existe porque a tela nunca deve
 * ficar sem um numero:</p>
 * <ol>
 *   <li>cache em memoria, valido por uma hora;</li>
 *   <li>o ultimo valor conhecido daquele par, marcado como desatualizado;</li>
 *   <li>um valor fixo de emergencia, so para EUR/BRL, se nunca houve cotacao alguma.</li>
 * </ol>
 */
@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final String FONTE = "referencia de bancos centrais";
    private static final Locale PORTUGUES = Locale.forLanguageTag("pt-BR");

    private final RestClient cliente;
    private final IntegracoesProperties.Cambio propriedades;
    private final AppSettingRepository configuracoes;

    public ExchangeRateService(RestClient.Builder builder,
                               IntegracoesProperties propriedades,
                               AppSettingRepository configuracoes) {
        this.propriedades = propriedades.cambio();
        this.cliente = builder.baseUrl(this.propriedades.baseUrl()).build();
        this.configuracoes = configuracoes;
    }

    @Cacheable(value = "cotacao", key = "#de + '-' + #para", unless = "#result.desatualizada()")
    @Transactional
    public ExchangeRateResponse consultar(String de, String para) {
        String origem = Moedas.validar(de);
        String destino = Moedas.validar(para);
        if (origem.equals(destino)) {
            return new ExchangeRateResponse(origem, destino, BigDecimal.ONE, "", FONTE, true, false);
        }

        try {
            JsonNode resposta = cliente.get()
                    .uri("/rate/{de}/{para}", origem, destino)
                    .header("Accept", "application/json")
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.UNPROCESSABLE_CONTENT.value()
                                    || status.value() == HttpStatus.NOT_FOUND.value(),
                            (req, res) -> {
                                throw new BusinessException(
                                        "A fonte de cambio nao tem cotacao de " + origem + " para " + destino + ".");
                            })
                    .body(JsonNode.class);

            BigDecimal taxa = extrairTaxa(resposta);
            String data = resposta.path("date").asString("");

            guardarUltimaTaxa(origem, destino, taxa, data);
            return new ExchangeRateResponse(origem, destino, taxa, data, FONTE, true, false);
        } catch (BusinessException erro) {
            throw erro;
        } catch (RuntimeException erro) {
            log.warn("Servico de cambio indisponivel para {}/{}; usando o ultimo valor conhecido",
                    origem, destino, erro);
            return ultimaConhecida(origem, destino);
        }
    }

    /**
     * Moedas que a fonte de cambio conhece, para a tela de preferencias.
     *
     * <p>Se a fonte estiver fora do ar, cai na lista ISO 4217 da propria JVM: a tela
     * continua funcionando, e um par sem cotacao responde com aviso claro.</p>
     */
    // ponytail: a lista de reserva tambem fica uma hora no cache; trocar por cache proprio se incomodar.
    @Cacheable("moedas")
    public List<CurrencyResponse> moedas() {
        try {
            JsonNode resposta = cliente.get().uri("/currencies")
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(JsonNode.class);
            List<CurrencyResponse> moedas = new ArrayList<>();
            if (resposta != null) {
                resposta.forEach(moeda -> {
                    String codigo = moeda.path("iso_code").asString("");
                    if (codigo.matches("[A-Z]{3}")) {
                        moedas.add(new CurrencyResponse(codigo, nome(codigo, moeda.path("name").asString(codigo))));
                    }
                });
            }
            if (!moedas.isEmpty()) {
                moedas.sort(Comparator.comparing(CurrencyResponse::codigo));
                return moedas;
            }
        } catch (RuntimeException erro) {
            log.warn("Lista de moedas da fonte de cambio indisponivel; usando a lista ISO local", erro);
        }
        return Currency.getAvailableCurrencies().stream()
                .map(moeda -> new CurrencyResponse(moeda.getCurrencyCode(), moeda.getDisplayName(PORTUGUES)))
                .sorted(Comparator.comparing(CurrencyResponse::codigo))
                .toList();
    }

    /** Nome em portugues quando a JVM conhece a moeda; senao, o nome em ingles da fonte. */
    private static String nome(String codigo, String reserva) {
        try {
            return Currency.getInstance(codigo).getDisplayName(PORTUGUES);
        } catch (IllegalArgumentException erro) {
            return reserva;
        }
    }

    private BigDecimal extrairTaxa(JsonNode resposta) {
        if (resposta == null) {
            throw new IllegalStateException("Resposta de cambio vazia.");
        }
        BigDecimal taxa = resposta.path("rate").decimalValue();
        if (taxa == null || taxa.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Cotacao invalida.");
        }
        return taxa;
    }

    /** EUR/BRL mantem a chave antiga, para nao perder a ultima cotacao ja guardada. */
    private static String chave(String de, String para) {
        return ("ultima_cotacao_" + de + "_" + para).toLowerCase(Locale.ROOT);
    }

    private void guardarUltimaTaxa(String de, String para, BigDecimal taxa, String data) {
        String valor = taxa.toPlainString() + "|" + data;
        configuracoes.findById(chave(de, para)).ifPresentOrElse(
                registro -> {
                    registro.atualizar(valor);
                    configuracoes.save(registro);
                },
                () -> configuracoes.save(new AppSetting(chave(de, para), valor))
        );
    }

    private ExchangeRateResponse ultimaConhecida(String de, String para) {
        return configuracoes.findById(chave(de, para))
                .map(registro -> {
                    String[] partes = registro.getValue().split("\\|", 2);
                    return new ExchangeRateResponse(
                            de, para,
                            new BigDecimal(partes[0]),
                            partes.length > 1 ? partes[1] : "",
                            FONTE,
                            true,
                            true
                    );
                })
                .orElseGet(() -> {
                    if (!"EUR".equals(de) || !"BRL".equals(para)) {
                        throw new UpstreamException("A cotacao de " + de + " para " + para + " esta indisponivel agora.");
                    }
                    return new ExchangeRateResponse(
                            de, para, new BigDecimal(propriedades.padrao()), "", "valor temporario", true, true);
                });
    }
}
