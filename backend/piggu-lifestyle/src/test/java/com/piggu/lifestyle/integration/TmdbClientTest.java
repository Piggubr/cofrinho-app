package com.piggu.lifestyle.integration;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UpstreamException;
import com.piggu.lifestyle.api.dto.TmdbMovie;
import com.piggu.lifestyle.config.IntegracoesProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** O token do TMDB vai so no cabecalho; a chave v3, que iria na URL, desliga a busca. */
class TmdbClientTest {

    private static final String TOKEN_V4 = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0ZSJ9.assinatura";

    @Test
    @DisplayName("token v4 vai no Authorization e nunca na URL")
    void tokenNoCabecalho() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer tmdb = MockRestServiceServer.bindTo(builder).build();
        TmdbClient cliente = new TmdbClient(builder, propriedades(TOKEN_V4));

        tmdb.expect(requestTo(not(containsString("api_key"))))
                .andExpect(header("Authorization", "Bearer " + TOKEN_V4))
                .andRespond(withSuccess("{\"results\":[{\"id\":603,\"title\":\"Matrix\"}]}", MediaType.APPLICATION_JSON));

        assertThat(cliente.buscar("Matrix")).extracting("titulo").containsExactly("Matrix");
        tmdb.verify();
    }

    @Test
    @DisplayName("chave curta v3 deixa a busca desligada em vez de ir para a URL")
    void chaveV3Desliga() {
        IntegracoesProperties comV3 = propriedades("0123456789abcdef0123456789abcdef");

        assertThat(comV3.tmdb().habilitado()).isFalse();
        assertThatThrownBy(() -> new TmdbClient(RestClient.builder(), comV3).buscar("Matrix"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("sorteio filtra pelo genero e monta o poster e a nota")
    void sorteiaDoGenero() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer tmdb = MockRestServiceServer.bindTo(builder).build();
        TmdbClient cliente = new TmdbClient(builder, propriedades(TOKEN_V4));

        tmdb.expect(requestTo(containsString("with_genres=35")))
                .andRespond(withSuccess("""
                        {"results":[{"id":1,"title":""},
                          {"id":2,"title":"Amelie","release_date":"2001-04-25","poster_path":"/a.jpg",
                           "vote_average":7.86,"overview":"Paris"}]}""", MediaType.APPLICATION_JSON));

        TmdbMovie filme = cliente.sortear("35");
        assertThat(filme).isEqualTo(new TmdbMovie("2", "Amelie", "2001",
                "https://image.tmdb.org/t/p/w500/a.jpg", new BigDecimal("7.9"), "Paris"));
        tmdb.verify();
    }

    @Test
    @DisplayName("genero que nao e numero fica fora; sem resultado o sorteio avisa")
    void sorteioSemResultado() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer tmdb = MockRestServiceServer.bindTo(builder).build();
        TmdbClient cliente = new TmdbClient(builder, propriedades(TOKEN_V4));

        tmdb.expect(requestTo(not(containsString("with_genres"))))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.sortear("acao"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Nenhum filme");
    }

    @Test
    @DisplayName("token recusado e TMDB fora do ar viram erros de servico externo")
    void errosDoTmdb() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer tmdb = MockRestServiceServer.bindTo(builder).build();
        TmdbClient cliente = new TmdbClient(builder, propriedades(TOKEN_V4));

        tmdb.expect(requestTo(containsString("/search/movie"))).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        tmdb.expect(requestTo(containsString("/search/movie"))).andRespond(withServerError());
        tmdb.expect(requestTo(containsString("/search/movie"))).andRespond(pedido -> {
            throw new java.io.IOException("sem rede");
        });

        assertThatThrownBy(() -> cliente.buscar("Matrix"))
                .isInstanceOf(UpstreamException.class)
                .hasMessageContaining("credencial");
        assertThatThrownBy(() -> cliente.buscar("Matrix"))
                .isInstanceOf(UpstreamException.class)
                .hasMessageContaining("erro 500");
        assertThatThrownBy(() -> cliente.buscar("Matrix"))
                .isInstanceOf(UpstreamException.class)
                .hasMessageContaining("indisponivel");
        assertThatThrownBy(() -> cliente.buscar("M")).isInstanceOf(BusinessException.class);
    }

    private static IntegracoesProperties propriedades(String token) {
        return new IntegracoesProperties(new IntegracoesProperties.Tmdb("https://tmdb.test/3", token, null, null, null), null);
    }
}
