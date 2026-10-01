package com.piggu.identity.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.identity.api.dto.GoogleLoginRequest;
import com.piggu.identity.api.dto.PreferencesRequest;
import com.piggu.identity.api.dto.RefreshRequest;
import com.piggu.identity.api.dto.TokenResponse;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.domain.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrada e saida da conta.
 *
 * <p>Substitui a acao {@code auth} do Apps Script, que devolvia um session_token de
 * 30 dias usado diretamente em toda chamada. Agora sao dois tokens com papeis distintos:
 * um curto para as requisicoes e um longo apenas para renovar.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService servico;

    public AuthController(AuthService servico) {
        this.servico = servico;
    }

    @PostMapping("/google")
    public TokenResponse entrar(@Valid @RequestBody GoogleLoginRequest pedido,
                                @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return servico.entrarComGoogle(pedido.idToken(), userAgent);
    }

    @PostMapping("/refresh")
    public TokenResponse renovar(@Valid @RequestBody RefreshRequest pedido,
                                 @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return servico.renovar(pedido.refreshToken(), userAgent);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> sair(@Valid @RequestBody RefreshRequest pedido) {
        servico.sair(pedido.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse eu(@AuthUser CurrentUser usuario) {
        return servico.perfil(usuario.id());
    }

    /** Qualquer perfil escolhe a propria moeda; nao ha como mexer na de outra pessoa. */
    @PutMapping("/me/preferences")
    public UserResponse preferencias(@Valid @RequestBody PreferencesRequest pedido,
                                     @AuthUser CurrentUser usuario) {
        return servico.salvarPreferencias(usuario.id(), pedido);
    }
}
