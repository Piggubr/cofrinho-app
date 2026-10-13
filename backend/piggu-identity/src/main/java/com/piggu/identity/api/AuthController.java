package com.piggu.identity.api;

import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.identity.api.dto.CadastroRequest;
import com.piggu.identity.api.dto.DefinirSenhaRequest;
import com.piggu.identity.api.dto.GoogleLoginRequest;
import com.piggu.identity.api.dto.PreferencesRequest;
import com.piggu.identity.api.dto.SenhaLoginRequest;
import com.piggu.identity.api.dto.TokenResponse;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.config.JwtProperties;
import com.piggu.identity.domain.AuthService;
import com.piggu.identity.domain.ConfirmacaoDeEmail;
import com.piggu.identity.domain.DocumentosDeIdentidade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Entrada e saida da conta.
 *
 * <p>Substitui a acao {@code auth} do Apps Script, que devolvia um session_token de
 * 30 dias usado diretamente em toda chamada. Agora sao dois tokens com papeis distintos:
 * um curto para as requisicoes, que o front guarda so em memoria, e um longo apenas para
 * renovar, que vive num cookie HttpOnly; Secure; SameSite=Strict; Path=/api/auth.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Cookie do refresh: o JavaScript da pagina nao le, e so viaja para /api/auth. */
    public static final String COOKIE = "piggu_refresh";

    private final AuthService servico;
    private final ConfirmacaoDeEmail confirmacao;
    private final Duration validadeDaSessao;

    public AuthController(AuthService servico, ConfirmacaoDeEmail confirmacao, JwtProperties jwt) {
        this.servico = servico;
        this.confirmacao = confirmacao;
        this.validadeDaSessao = jwt.refreshTtl();
    }

    @PostMapping("/google")
    public ResponseEntity<TokenResponse> entrar(@Valid @RequestBody GoogleLoginRequest pedido,
                                                @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        boolean lembrar = !Boolean.FALSE.equals(pedido.lembrar());
        return comCookie(servico.entrarComGoogle(pedido.idToken(), pedido.versaoDosTermos(), lembrar, userAgent));
    }

    /** Conta nova por e-mail e senha; o aceite dos termos vem junto. */
    @PostMapping("/cadastro")
    public ResponseEntity<TokenResponse> cadastrar(@Valid @RequestBody CadastroRequest pedido,
                                                   @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        boolean lembrar = !Boolean.FALSE.equals(pedido.lembrar());
        String pais = pedido.pais() == null ? DocumentosDeIdentidade.BRASIL : pedido.pais();
        return comCookie(servico.cadastrar(pedido.nome(), pedido.email(), pedido.senha(), pais, pedido.documento(),
                pedido.telefone(), pedido.versaoDosTermos(), lembrar, userAgent));
    }

    public record ConfirmarEmailRequest(@NotBlank @Size(max = 100) String token) {
    }

    /** Aberto: quem clica no link pode estar sem sessao, em outro aparelho. */
    @PostMapping("/confirmar-email")
    public UserResponse confirmarEmail(@Valid @RequestBody ConfirmarEmailRequest pedido) {
        return confirmacao.confirmar(pedido.token());
    }

    /** Manda outro link; o anterior deixa de valer. */
    @PostMapping("/me/email/reenviar")
    public ResponseEntity<Void> reenviarConfirmacao(@AuthUser CurrentUser usuario) {
        confirmacao.reenviar(usuario.id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/entrar")
    public ResponseEntity<TokenResponse> entrarComSenha(@Valid @RequestBody SenhaLoginRequest pedido,
                                                        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        boolean lembrar = !Boolean.FALSE.equals(pedido.lembrar());
        return comCookie(servico.entrarComSenha(pedido.email(), pedido.senha(), lembrar, userAgent));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> renovar(@CookieValue(name = COOKIE, required = false) String refresh,
                                                 @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        if (refresh == null || refresh.isBlank()) {
            throw new UnauthorizedException("Sua sessao venceu. Entre novamente.");
        }
        return comCookie(servico.renovar(refresh, userAgent));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> sair(@CookieValue(name = COOKIE, required = false) String refresh) {
        if (refresh != null && !refresh.isBlank()) {
            servico.sair(refresh);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<TokenResponse> comCookie(TokenResponse par) {
        // Sem "continuar conectado" o cookie e de sessao: some quando o navegador fecha.
        ResponseCookie cookie = cookie(par.refreshToken(), par.lembrar() ? validadeDaSessao : null);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(par);
    }

    private static ResponseCookie cookie(String valor, Duration validade) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(COOKIE, valor)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth");
        if (validade != null) {
            cookie.maxAge(validade);
        }
        return cookie.build();
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

    /**
     * Cria a senha de quem so entrava pelo Google, ou troca a atual. O cookie da sessao
     * deste aparelho (o caminho e /api/auth, entao ele vem junto) diz qual sessao fica.
     */
    @PutMapping("/me/senha")
    public UserResponse definirSenha(@Valid @RequestBody DefinirSenhaRequest pedido,
                                     @AuthUser CurrentUser usuario,
                                     @CookieValue(name = COOKIE, required = false) String refresh) {
        return servico.definirSenha(usuario.id(), pedido.senhaAtual(), pedido.novaSenha(), refresh);
    }

    public record ModulosRequest(@NotNull(message = "Escolha os modulos.") @Size(max = 10) List<String> modulos) {
    }

    /** Liga e desliga as telas de estilo de vida no menu da propria pessoa. */
    @PutMapping("/me/modules")
    public UserResponse modulos(@Valid @RequestBody ModulosRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.escolherModulos(usuario.id(), pedido.modulos());
    }
}
