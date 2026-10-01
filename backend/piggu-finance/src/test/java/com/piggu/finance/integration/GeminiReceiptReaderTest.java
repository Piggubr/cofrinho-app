package com.piggu.finance.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UpstreamException;
import com.piggu.finance.api.dto.ReceiptParseRequest;
import com.piggu.finance.api.dto.ReceiptParseResponse;
import com.piggu.finance.config.IntegracoesProperties;
import com.piggu.finance.domain.CategoryService;
import com.piggu.finance.domain.ProductMemoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Leitura de recibo com o Gemini simulado. */
class GeminiReceiptReaderTest {

    private static final String URL = "https://gemini.test/v1beta/models/modelo:generateContent";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer gemini = MockRestServiceServer.bindTo(builder).build();
    private final CategoryService categorias = mock(CategoryService.class);
    private final ProductMemoryService memoria = mock(ProductMemoryService.class);

    private GeminiReceiptReader leitor(String chave) {
        when(categorias.listar()).thenReturn(List.of("Mercado", "Outros"));
        when(categorias.normalizar(anyString())).thenAnswer(pedido -> pedido.getArgument(0));
        when(memoria.memoriaParaIa(anyInt())).thenReturn(List.of());
        return new GeminiReceiptReader(builder,
                new IntegracoesProperties(new IntegracoesProperties.Gemini("https://gemini.test", chave, "modelo", null), null),
                categorias, memoria, new ObjectMapper());
    }

    @Test
    @DisplayName("a chave vai no cabecalho, nunca na URL, e os itens voltam lidos")
    void chaveNoCabecalho() {
        gemini.expect(requestTo(URL))
                .andExpect(header("x-goog-api-key", "chave-secreta"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":
                        "{\\"estabelecimento\\":\\"Pingo Doce\\",\\"data\\":\\"2026-09-30\\",\\"itens\\":[{\\"item\\":\\"Leite\\",\\"categoria\\":\\"Mercado\\",\\"valor\\":1.29}]}"
                        }]}}]}
                        """, MediaType.APPLICATION_JSON));

        ReceiptParseResponse lido = leitor("chave-secreta").ler(new ReceiptParseRequest("aW1n", "image/png"));

        assertThat(lido.estabelecimento()).isEqualTo("Pingo Doce");
        assertThat(lido.itens()).singleElement().satisfies(item -> {
            assertThat(item.item()).isEqualTo("Leite");
            assertThat(item.valor()).isEqualByComparingTo("1.29");
        });
        gemini.verify();
    }

    @Test
    @DisplayName("chave recusada vira aviso de configuracao, sem expor detalhe")
    void chaveRecusada() {
        gemini.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> leitor("errada").ler(new ReceiptParseRequest("aW1n", "image/png")))
                .isInstanceOf(UpstreamException.class)
                .hasMessageContaining("chave do Gemini foi recusada");
    }

    @Test
    @DisplayName("sem chave configurada nem chama o Gemini")
    void semChave() {
        assertThatThrownBy(() -> leitor("").ler(new ReceiptParseRequest("aW1n", "image/png")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nao esta configurada");
        gemini.verify();
    }
}
