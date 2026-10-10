package com.piggu.lifestyle.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.lifestyle.api.dto.MovieRatingRequest;
import com.piggu.lifestyle.api.dto.MovieResponse;
import com.piggu.lifestyle.api.dto.TmdbMovie;
import com.piggu.lifestyle.domain.MovieService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Filmes. Substitui searchMovies, randomMovie, saveMovie, toggleMovieWatched,
 * rateMovie e deleteMovie.
 */
@RestController
@RequestMapping("/api/movies")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR', 'PARCEIRO')")
public class MovieController {

    private final MovieService servico;

    public MovieController(MovieService servico) {
        this.servico = servico;
    }

    @GetMapping
    public List<MovieResponse> listar() {
        return servico.listar();
    }

    /** Busca no TMDB, sem gravar nada na lista. */
    @GetMapping("/search")
    public List<TmdbMovie> buscar(@RequestParam String busca) {
        return servico.buscarNoCatalogo(busca);
    }

    /** @param genero id de genero do TMDB; ausente sorteia entre todos */
    @GetMapping("/random")
    public TmdbMovie sortear(@RequestParam(required = false) String genero) {
        return servico.sortear(genero);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse adicionar(@Valid @RequestBody TmdbMovie filme, @AuthUser CurrentUser usuario) {
        return servico.adicionar(filme, usuario.email());
    }

    @PatchMapping("/{id}/watched")
    public MovieResponse marcarAssistido(@PathVariable UUID id, @RequestParam boolean assistido) {
        return servico.marcarAssistido(id, assistido);
    }

    @PutMapping("/{id}/rating")
    public MovieResponse avaliar(@PathVariable UUID id,
                                 @Valid @RequestBody MovieRatingRequest pedido,
                                 @AuthUser CurrentUser usuario) {
        return servico.avaliar(id, pedido.nota(), usuario.email());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        servico.excluir(id, usuario);
        return ResponseEntity.noContent().build();
    }
}
