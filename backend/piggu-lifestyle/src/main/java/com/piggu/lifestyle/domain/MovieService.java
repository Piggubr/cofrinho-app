package com.piggu.lifestyle.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Texto;
import com.piggu.lifestyle.api.dto.MovieResponse;
import com.piggu.lifestyle.api.dto.TmdbMovie;
import com.piggu.lifestyle.integration.TmdbClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Lista de filmes do casal.
 *
 * <p>Porte de salvarFilme_, alternarFilmeVisto_, avaliarFilme_, excluirFilme_ e
 * carregarFilmes_. A busca e o sorteio ficam no TmdbClient.</p>
 */
@Service
public class MovieService {

    private final MovieRepository repositorio;
    private final TmdbClient tmdb;

    public MovieService(MovieRepository repositorio, TmdbClient tmdb) {
        this.repositorio = repositorio;
        this.tmdb = tmdb;
    }

    @Transactional(readOnly = true)
    public List<MovieResponse> listar() {
        return repositorio.findAllByOrderByCreatedAtDesc().stream().map(MovieResponse::de).toList();
    }

    public List<TmdbMovie> buscarNoCatalogo(String termo) {
        return tmdb.buscar(termo);
    }

    public TmdbMovie sortear(String genero) {
        return tmdb.sortear(genero);
    }

    @Transactional
    public MovieResponse adicionar(TmdbMovie filme, UUID usuarioId) {
        String titulo = Texto.limitar(filme.titulo(), 200);
        if (titulo.isEmpty()) {
            throw new BusinessException("O filme nao chegou corretamente.");
        }

        String tmdbId = Texto.limitar(filme.tmdbId(), 20);
        if (!tmdbId.isEmpty() && repositorio.findByTmdbId(tmdbId).isPresent()) {
            throw new BusinessException("Esse filme ja esta na lista.");
        }

        Movie novo = new Movie(
                tmdbId,
                titulo,
                Texto.limitar(filme.ano(), 4),
                posterDoTmdb(filme.poster()),
                filme.nota() == null ? BigDecimal.ZERO : filme.nota(),
                Texto.limitar(filme.sinopse(), 1000),
                usuarioId
        );
        return MovieResponse.de(repositorio.save(novo));
    }

    /**
     * O poster chega do navegador e vira tag img para a familia inteira: so a capa do
     * TMDB passa, senao um host qualquer receberia o IP de todos.
     */
    static String posterDoTmdb(String url) {
        String limpo = Texto.limitar(url, 500);
        return limpo.startsWith("https://image.tmdb.org/") && !limpo.matches(".*[\\s\"'<>].*") ? limpo : "";
    }

    @Transactional
    public MovieResponse marcarAssistido(UUID id, boolean assistido) {
        Movie filme = buscar(id);
        filme.marcarAssistido(assistido);
        return MovieResponse.de(repositorio.save(filme));
    }

    /** Cada pessoa tem a propria nota; avaliar de novo substitui a anterior. */
    @Transactional
    public MovieResponse avaliar(UUID id, int nota, UUID usuarioId) {
        Movie filme = buscar(id);
        filme.avaliar(usuarioId, nota);
        return MovieResponse.de(repositorio.save(filme));
    }

    @Transactional
    public void excluir(UUID id, CurrentUser usuario) {
        Movie filme = buscar(id);
        if (!usuario.podeGerenciar(filme.getUserId())) {
            throw new ForbiddenException("Voce nao pode apagar este filme.");
        }
        repositorio.delete(filme);
    }

    private Movie buscar(UUID id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new NotFoundException("Filme nao encontrado."));
    }
}
