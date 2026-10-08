package com.piggu.lifestyle.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.lifestyle.api.dto.MovieResponse;
import com.piggu.lifestyle.api.dto.TmdbMovie;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lista de filmes.
 *
 * <p>A nota de cada pessoa fica em um mapa de e-mail para nota, guardado em jsonb.
 * Vale testar que o mapa sobrevive a ida e volta do banco, porque um erro de
 * mapeamento so apareceria ao reler.</p>
 */
class MovieServiceTest extends PostgresIntegrationTest {

    private static final String TITULAR = "titular@piggu.test";
    private static final String EDUARDO = "eduardo@piggu.test";

    @Autowired
    private MovieService filmes;

    @Autowired
    private MovieRepository repositorio;

    @BeforeEach
    void limpar() {
        repositorio.deleteAll();
    }

    @Test
    @DisplayName("filme e adicionado com os dados do catalogo")
    void adicionaFilme() {
        MovieResponse filme = adicionar("603", "Matrix");

        assertThat(filme.titulo()).isEqualTo("Matrix");
        assertThat(filme.tmdbId()).isEqualTo("603");
        assertThat(filme.assistido()).isFalse();
        assertThat(filme.avaliacoes()).isEmpty();
    }

    @Test
    @DisplayName("o mesmo filme do catalogo nao entra duas vezes")
    void naoDuplicaFilmeDoCatalogo() {
        adicionar("603", "Matrix");

        assertThatThrownBy(() -> adicionar("603", "Matrix Reloaded"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Esse filme ja esta na lista.");
    }

    @Test
    @DisplayName("filme sem id de catalogo pode repetir titulo, por ser entrada manual")
    void filmeSemTmdbIdPodeRepetir() {
        adicionar("", "Curta caseiro");
        adicionar("", "Curta caseiro");

        assertThat(filmes.listar()).hasSize(2);
    }

    @Test
    @DisplayName("filme sem titulo e recusado")
    void filmeSemTitulo() {
        assertThatThrownBy(() -> adicionar("1", "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O filme nao chegou corretamente.");
    }

    @Test
    @DisplayName("cada pessoa tem a propria nota, e as duas convivem")
    void notasPorPessoa() {
        MovieResponse filme = adicionar("603", "Matrix");

        filmes.avaliar(filme.id(), 5, TITULAR);
        MovieResponse depois = filmes.avaliar(filme.id(), 4, EDUARDO);

        assertThat(depois.avaliacoes()).containsEntry(TITULAR, 5).containsEntry(EDUARDO, 4);
        assertThat(filmes.listar().get(0).avaliacoes()).hasSize(2);
    }

    @Test
    @DisplayName("avaliar de novo substitui a nota anterior da mesma pessoa")
    void reavaliarSubstitui() {
        MovieResponse filme = adicionar("603", "Matrix");

        filmes.avaliar(filme.id(), 3, TITULAR);
        MovieResponse depois = filmes.avaliar(filme.id(), 5, TITULAR);

        assertThat(depois.avaliacoes()).hasSize(1).containsEntry(TITULAR, 5);
    }

    @Test
    @DisplayName("marcar assistido e reversivel")
    void marcarAssistido() {
        MovieResponse filme = adicionar("603", "Matrix");

        assertThat(filmes.marcarAssistido(filme.id(), true).assistido()).isTrue();
        assertThat(filmes.marcarAssistido(filme.id(), false).assistido()).isFalse();
    }

    @Test
    @DisplayName("membro nao apaga filme de outra pessoa, mas o titular e o admin apagam")
    void exclusaoRespeitaDono() {
        MovieResponse filme = adicionar("603", "Matrix");
        CurrentUser outra = new CurrentUser(UUID.randomUUID(), EDUARDO, PigguRole.MEMBRO);
        CurrentUser admin = new CurrentUser(UUID.randomUUID(), "admin@piggu.test", PigguRole.ADMIN);

        assertThatThrownBy(() -> filmes.excluir(filme.id(), outra)).isInstanceOf(ForbiddenException.class);

        filmes.excluir(filme.id(), admin);
        assertThat(filmes.listar()).isEmpty();
    }

    private MovieResponse adicionar(String tmdbId, String titulo) {
        return filmes.adicionar(new TmdbMovie(
                tmdbId, titulo, "1999", "https://img.test/p.jpg",
                new BigDecimal("8.7"), "Sinopse"), TITULAR);
    }
}
