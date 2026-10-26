package com.piggu.lifestyle.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.lifestyle.api.dto.MovieRatingRequest;
import com.piggu.lifestyle.api.dto.MovieResponse;
import com.piggu.lifestyle.api.dto.TmdbMovie;
import com.piggu.lifestyle.domain.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@Tag(name = "Filmes", description = "Lista de filmes da família, com busca no TMDB.")
@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieService servico;

    public MovieController(MovieService servico) {
        this.servico = servico;
    }

    @Operation(summary = "Listar filmes")
    @GetMapping
    public List<MovieResponse> listar() {
        return servico.listar();
    }

    /** Busca no TMDB, sem gravar nada na lista. */
    @Operation(summary = "Buscar filme no TMDB")
    @GetMapping("/search")
    public List<TmdbMovie> buscar(@RequestParam String busca) {
        return servico.buscarNoCatalogo(busca);
    }

    /** @param genero id de genero do TMDB; ausente sorteia entre todos */
    @Operation(summary = "Sortear um filme")
    @GetMapping("/random")
    public TmdbMovie sortear(@RequestParam(required = false) String genero) {
        return servico.sortear(genero);
    }

    @Operation(summary = "Adicionar filme à lista")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse adicionar(@Valid @RequestBody TmdbMovie filme, @AuthUser CurrentUser usuario) {
        return servico.adicionar(filme, usuario.id());
    }

    @Operation(summary = "Marcar ou desmarcar filme como assistido")
    @PatchMapping("/{id}/watched")
    public MovieResponse marcarAssistido(@PathVariable UUID id, @RequestParam boolean assistido) {
        return servico.marcarAssistido(id, assistido);
    }

    @Operation(summary = "Avaliar filme")
    @PutMapping("/{id}/rating")
    public MovieResponse avaliar(@PathVariable UUID id,
                                 @Valid @RequestBody MovieRatingRequest pedido,
                                 @AuthUser CurrentUser usuario) {
        return servico.avaliar(id, pedido.nota(), usuario.id());
    }

    @Operation(summary = "Excluir filme")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        servico.excluir(id, usuario);
        return ResponseEntity.noContent().build();
    }
}
