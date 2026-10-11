package com.piggu.identity.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.avisos.Aparelhos;
import com.piggu.identity.domain.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Avisos fora do app: os canais que a pessoa quer e os aparelhos que recebem push. */
@RestController
@RequestMapping("/api/auth/me")
public class AvisosController {

    private final AuthService servico;
    private final Aparelhos aparelhos;

    public AvisosController(AuthService servico, Aparelhos aparelhos) {
        this.servico = servico;
        this.aparelhos = aparelhos;
    }

    public record AvisosRequest(@NotNull Boolean email, @NotNull Boolean push) {
    }

    /** Token do APNs: 64 caracteres hexadecimais hoje, com folga para a Apple mudar. */
    public record AparelhoRequest(
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{32,200}", message = "Token de aparelho invalido.") String token,
            @NotBlank @Pattern(regexp = "IOS", message = "Plataforma nao suportada.") String plataforma) {
    }

    @PutMapping("/alerts")
    public UserResponse avisos(@Valid @RequestBody AvisosRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.escolherAvisos(usuario.id(), pedido.email(), pedido.push());
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrar(@Valid @RequestBody AparelhoRequest pedido, @AuthUser CurrentUser usuario) {
        aparelhos.registrar(usuario.id(), pedido.token().toLowerCase(), pedido.plataforma());
    }

    @DeleteMapping("/devices/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable String token, @AuthUser CurrentUser usuario) {
        aparelhos.remover(usuario.id(), token.toLowerCase());
    }
}
