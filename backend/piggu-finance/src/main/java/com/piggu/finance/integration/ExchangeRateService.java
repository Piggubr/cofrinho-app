package com.piggu.finance.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.piggu.finance.api.dto.ExchangeRateResponse;
import com.piggu.finance.config.IntegracoesProperties;
import com.piggu.finance.domain.AppSetting;
import com.piggu.finance.domain.AppSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Cotacao de referencia EUR para BRL.
 *
 * <p>Porte de carregarCotacaoReferencia_, com a mesma escada de tres degraus que o
 * original tinha, e que existe porque a tela nunca deve ficar sem um numero:</p>
 * <ol>
 *   <li>cache em memoria, valido por uma hora;</li>
 *   <li>o ultimo valor conhecido, marcado como desatualizado;</li>
 *   <li>um valor fixo de emergencia, se nunca houve cotacao alguma.</li>
 * </ol>
 */
@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final String CHAVE_ULTIMA_COTACAO = "ultima_cotacao_eur_brl";
    private static final String FONTE = "referencia de bancos centrais";

    private final RestClient cliente;
    private final IntegracoesProperties.Cambio propriedades;
    private final AppSettingRepository configuracoes;

    public ExchangeRateService(RestClient.Builder builder,
                               IntegracoesProperties propriedades,
                               AppSettingRepository configuracoes) {
        this.propriedades = propriedades.cambio();
        this.cliente = builder.build();
        this.configuracoes = configuracoes;
    }

    @Cacheable(value = "cotacao", unless = "#result.desatualizada()")
    @Transactional
    public ExchangeRateResponse consultar() {
        try {
            JsonNode resposta = cliente.get()
                    .uri(propriedades.url())
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(JsonNode.class);

            BigDecimal taxa = extrairTaxa(resposta);
            String data = resposta.path("date").asText("");

            guardarUltimaTaxa(taxa, data);
            return new ExchangeRateResponse(taxa, data, FONTE, true, false);
        } catch (RuntimeException erro) {
            log.warn("Servico de cambio indisponivel; usando o ultimo valor conhecido", erro);
            return ultimaConhecida();
        }
    }

    private BigDecimal extrairTaxa(JsonNode resposta) {
        if (resposta == null) {
            throw new IllegalStateException("Resposta de cambio vazia.");
        }
        // A v2 da Frankfurter devolve {"rate": 6.1}; a v1 devolvia {"rates": {"BRL": 6.1}}.
        JsonNode direto = resposta.path("rate");
        BigDecimal taxa = direto.isMissingNode() || direto.isNull()
                ? resposta.path("rates").path("BRL").decimalValue()
                : direto.decimalValue();

        if (taxa == null || taxa.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Cotacao invalida.");
        }
        return taxa;
    }

    private void guardarUltimaTaxa(BigDecimal taxa, String data) {
        String valor = taxa.toPlainString() + "|" + data;
        configuracoes.findById(CHAVE_ULTIMA_COTACAO).ifPresentOrElse(
                registro -> {
                    registro.atualizar(valor);
                    configuracoes.save(registro);
                },
                () -> configuracoes.save(new AppSetting(CHAVE_ULTIMA_COTACAO, valor))
        );
    }

    private ExchangeRateResponse ultimaConhecida() {
        return configuracoes.findById(CHAVE_ULTIMA_COTACAO)
                .map(registro -> {
                    String[] partes = registro.getValue().split("\\|", 2);
                    return new ExchangeRateResponse(
                            new BigDecimal(partes[0]),
                            partes.length > 1 ? partes[1] : "",
                            FONTE,
                            true,
                            true
                    );
                })
                .orElseGet(() -> new ExchangeRateResponse(
                        new BigDecimal(propriedades.padrao()), "", "valor temporario", true, true));
    }
}
