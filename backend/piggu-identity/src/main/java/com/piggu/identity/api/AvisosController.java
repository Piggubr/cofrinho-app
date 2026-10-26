package com.piggu.identity.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.avisos.Aparelhos;
import com.piggu.identity.domain.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Avisos", description = "Canais de aviso (e-mail e push) e aparelhos que recebem push.")
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

    /**
     * Token do aparelho. IOS: o do APNs, 64 caracteres hexadecimais hoje. ANDROID: o do Firebase
     * (FCM), com letras, numeros, ':', '-' e '_', na casa dos 160 caracteres. O limite tem folga para
     * os dois mudarem, e nenhum caractere aceito precisa de escape no endereco do envio.
     */
    public record AparelhoRequest(
            @NotBlank @Pattern(regexp = "[0-9A-Za-z:_-]{32,512}", message = "Token de aparelho invalido.") String token,
            @NotBlank @Pattern(regexp = "IOS|ANDROID", message = "Plataforma nao suportada.") String plataforma) {

        /** O token do APNs nao diferencia maiuscula; o do FCM diferencia e vai como veio. */
        String tokenGuardado() {
            return "IOS".equals(plataforma) ? token.toLowerCase() : token;
        }
    }

    @Operation(summary = "Escolher os canais de aviso")
    @PutMapping("/alerts")
    public UserResponse avisos(@Valid @RequestBody AvisosRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.escolherAvisos(usuario.id(), pedido.email(), pedido.push());
    }

    @Operation(summary = "Registrar o aparelho para receber push")
    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrar(@Valid @RequestBody AparelhoRequest pedido, @AuthUser CurrentUser usuario) {
        servico.exigirEmailConfirmado(usuario.id());
        aparelhos.registrar(usuario.id(), pedido.tokenGuardado(), pedido.plataforma());
    }

    @Operation(summary = "Parar de mandar push para o aparelho")
    @DeleteMapping("/devices/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable String token, @AuthUser CurrentUser usuario) {
        aparelhos.remover(usuario.id(), token);
    }
}
