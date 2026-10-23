package com.piggu.lifestyle.integration;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UpstreamException;
import com.piggu.lifestyle.api.dto.CatalogProduct;
import com.piggu.lifestyle.config.IntegracoesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Busca no OpenFoodFacts: o que vira produto, e a falha do catalogo vira 502 para o app. */
class CatalogClientTest {

    private MockRestServiceServer catalogo;
    private CatalogClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        catalogo = MockRestServiceServer.bindTo(builder).build();
        cliente = new CatalogClient(builder, new IntegracoesProperties(null, null));
    }

    @Test
    @DisplayName("produto sem nome fica de fora; a marca e a primeira da lista e a foto pequena vem antes")
    void extraiOsProdutos() {
        catalogo.expect(requestTo(containsString("search_terms=leite")))
                .andExpect(header("User-Agent", "Piggu/1.0 (contato@piggu.app)"))
                .andRespond(withSuccess("""
                        {"products":[
                          {"code":"1","product_name":" Leite integral ","brands":"Mimosa, Lactogal",
                           "quantity":"1 L","image_small_url":"https://img/p.jpg","image_url":"https://img/g.jpg"},
                          {"code":"2","product_name":"","generic_name":"Leite magro","image_url":"https://img/g2.jpg"},
                          {"code":"3","product_name":"  "}
                        ]}""", MediaType.APPLICATION_JSON));

        assertThat(cliente.buscar("leite")).containsExactly(
                new CatalogProduct("1", "Leite integral", "Mimosa", "1 L", "https://img/p.jpg"),
                new CatalogProduct("2", "Leite magro", "", "", "https://img/g2.jpg"));
        catalogo.verify();
    }

    @Test
    @DisplayName("no maximo oito produtos")
    void limitaOsResultados() {
        StringBuilder produtos = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            produtos.append(i == 0 ? "" : ",").append("{\"code\":\"").append(i)
                    .append("\",\"product_name\":\"Produto ").append(i).append("\"}");
        }
        catalogo.expect(requestTo(containsString("search.pl")))
                .andRespond(withSuccess("{\"products\":[" + produtos + "]}", MediaType.APPLICATION_JSON));

        assertThat(cliente.buscar("produto")).hasSize(8);
    }

    @Test
    @DisplayName("resposta vazia nao e erro: so nao ha produtos")
    void respostaVazia() {
        catalogo.expect(requestTo(containsString("search.pl"))).andRespond(withSuccess());

        assertThat(cliente.buscar("arroz")).isEmpty();
    }

    @Test
    @DisplayName("menos de duas letras nem chega ao catalogo")
    void termoCurto() {
        assertThatThrownBy(() -> cliente.buscar(" a "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("duas letras");
        catalogo.verify();
    }

    @Test
    @DisplayName("catalogo fora do ar vira erro de servico externo")
    void catalogoForaDoAr() {
        catalogo.expect(requestTo(containsString("search.pl"))).andRespond(withServerError());

        assertThatThrownBy(() -> cliente.buscar("feijao"))
                .isInstanceOf(UpstreamException.class)
                .hasMessageContaining("indisponivel");
    }

    @Test
    @DisplayName("falha de rede tambem vira erro de servico externo")
    void falhaDeRede() {
        catalogo.expect(requestTo(containsString("search.pl")))
                .andRespond(pedido -> {
                    throw new java.io.IOException("conexao recusada");
                });

        assertThatThrownBy(() -> cliente.buscar("feijao")).isInstanceOf(UpstreamException.class);
    }
}
