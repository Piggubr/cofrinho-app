package com.piggu.identity.api;

import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.LimiteDeRequisicoes;
import com.piggu.common.web.Texto;
import com.piggu.identity.api.dto.AppleLoginRequest;
import com.piggu.identity.api.dto.CadastroRequest;
import com.piggu.identity.api.dto.DefinirSenhaRequest;
import com.piggu.identity.api.dto.GoogleLoginRequest;
import com.piggu.identity.api.dto.PreferencesRequest;
import com.piggu.identity.api.dto.RefreshRequest;
import com.piggu.identity.api.dto.SenhaLoginRequest;
import com.piggu.identity.api.dto.TokenResponse;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.config.JwtProperties;
import com.piggu.identity.domain.AuthService;
import com.piggu.identity.domain.ConfirmacaoDeEmail;
import com.piggu.identity.domain.Paises;
import com.piggu.identity.domain.RedefinicaoDeSenha;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
import java.util.UUID;

/**
 * Entrada e saida da conta.
 *
 * <p>Substitui a acao {@code auth} do Apps Script, que devolvia um session_token de
 * 30 dias usado diretamente em toda chamada. Agora sao dois tokens com papeis distintos:
 * um curto para as requisicoes, que o front guarda so em memoria, e um longo apenas para
 * renovar, que vive num cookie HttpOnly; Secure; SameSite=Strict; Path=/api/auth.</p>
 *
 * <p>O app nativo nao tem esse cookie (D-058): ele pede o refresh no corpo com
 * {@code refreshNoCorpo} no login e o devolve no corpo de renovar, sair e trocar a senha. O
 * refresh que veio pelo cookie nunca vai para o corpo, peca o cliente o que pedir: um XSS no
 * site conseguiria renovar, mas continua sem ver o refresh.</p>
 */
@Tag(name = "Login e sessão", description = "Entrar, renovar e sair; perfil e preferências de quem está logado.")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Cookie do refresh: o JavaScript da pagina nao le, e so viaja para /api/auth. */
    public static final String COOKIE = "piggu_refresh";

    private final AuthService servico;
    private final ConfirmacaoDeEmail confirmacao;
    private final RedefinicaoDeSenha redefinicao;
    private final Duration validadeDaSessao;
    private final LimiteDeRequisicoes.Limites limites;

    public AuthController(AuthService servico, ConfirmacaoDeEmail confirmacao, RedefinicaoDeSenha redefinicao,
                          JwtProperties jwt, LimiteDeRequisicoes.Limites limites) {
        this.servico = servico;
        this.confirmacao = confirmacao;
        this.redefinicao = redefinicao;
        this.validadeDaSessao = jwt.refreshTtl();
        this.limites = limites;
    }

    @Operation(summary = "Entrar com o Google (ID token)")
    @SecurityRequirements
    @PostMapping("/google")
    public ResponseEntity<TokenResponse> entrar(@Valid @RequestBody GoogleLoginRequest pedido,
                                                @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        boolean lembrar = !Boolean.FALSE.equals(pedido.lembrar());
        return responder(servico.entrarComGoogle(pedido.idToken(), pedido.versaoDosTermos(), lembrar, userAgent),
                pedido.refreshNoCorpo());
    }

    /** Para o app nativo do iPhone; sem {@code piggu.apple.client-ids} configurado, responde 501. */
    @Operation(summary = "Entrar com a Apple (identity token)",
            description = "Responde 501 (código EM_BREVE) enquanto o servidor não tiver o Bundle ID em APPLE_CLIENT_IDS.")
    @SecurityRequirements
    @PostMapping("/apple")
    public ResponseEntity<TokenResponse> entrarComApple(@Valid @RequestBody AppleLoginRequest pedido,
                                                        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        boolean lembrar = !Boolean.FALSE.equals(pedido.lembrar());
        return responder(servico.entrarComApple(pedido.idToken(), pedido.nonce(), pedido.nome(), pedido.codigo(),
                pedido.versaoDosTermos(), lembrar, userAgent), pedido.refreshNoCorpo());
    }

    /**
     * Conta nova por e-mail e senha; o aceite dos termos vem junto. Responde 202 sem
     * sessao, a mesma coisa se o e-mail ja tem conta: a pessoa confirma o e-mail e entra.
     */
    @Operation(summary = "Criar conta com e-mail e senha")
    @ApiResponse(responseCode = "202", description = "Pedido aceito, com ou sem conta nesse e-mail: a pessoa confirma o e-mail e depois entra")
    @SecurityRequirements
    @PostMapping("/cadastro")
    public ResponseEntity<Void> cadastrar(@Valid @RequestBody CadastroRequest pedido) {
        String pais = pedido.pais() == null ? Paises.BRASIL : pedido.pais();
        servico.cadastrar(pedido.nome(), pedido.email(), pedido.senha(), pais, pedido.documento(),
                pedido.telefone(), pedido.versaoDosTermos(), pedido.nascimento());

        return ResponseEntity.accepted().build();
    }

    public record ConfirmarEmailRequest(@NotBlank @Size(max = 100) String token) {
    }

    /** Aberto, mas conta com senha so confirma com a sessao dela (D-152); sem ela, 403 ENTRE_PARA_CONFIRMAR. */
    @Operation(summary = "Confirmar o e-mail pelo link")
    @SecurityRequirements
    @PostMapping("/confirmar-email")
    public ResponseEntity<Void> confirmarEmail(@Valid @RequestBody ConfirmarEmailRequest pedido,
                                               @AuthenticationPrincipal Jwt sessao) {
        // Nada do perfil volta: quem tem o link pode nao ser o dono da sessao.
        confirmacao.confirmar(pedido.token(), sessao == null ? null : UUID.fromString(sessao.getSubject()));
        return ResponseEntity.noContent().build();
    }

    /** Manda outro link; o anterior deixa de valer. */
    @Operation(summary = "Reenviar o link de confirmação do e-mail")
    @PostMapping("/me/email/reenviar")
    public ResponseEntity<Void> reenviarConfirmacao(@AuthUser CurrentUser usuario) {
        confirmacao.reenviar(usuario.id());
        return ResponseEntity.noContent().build();
    }

    public record EsqueciSenhaRequest(@NotBlank(message = "Informe seu e-mail.") @Size(max = 320) String email) {
    }

    /** Aberto, e responde 202 com ou sem conta: quem esqueceu a senha nao tem sessao. */
    @Operation(summary = "Pedir o link para redefinir a senha")
    @ApiResponse(responseCode = "202", description = "Pedido aceito, com ou sem conta nesse e-mail")
    @SecurityRequirements
    @PostMapping("/senha/esqueci")
    public ResponseEntity<Void> esqueciSenha(@Valid @RequestBody EsqueciSenhaRequest pedido) {
        redefinicao.pedir(pedido.email());
        return ResponseEntity.accepted().build();
    }

    public record RedefinirSenhaRequest(@NotBlank @Size(max = 100) String token,
                                        @NotBlank(message = "Informe a nova senha.") @Size(max = 200, message = "Senha longa demais.") String novaSenha) {
    }

    /** Senha nova pelo link do e-mail; sem sessao nova: a pessoa entra com a senha que criou. */
    @Operation(summary = "Redefinir a senha pelo link do e-mail")
    @SecurityRequirements
    @PostMapping("/senha/redefinir")
    public ResponseEntity<Void> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest pedido) {
        redefinicao.redefinir(pedido.token(), pedido.novaSenha());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Entrar com e-mail e senha")
    @SecurityRequirements
    @PostMapping("/entrar")
    public ResponseEntity<TokenResponse> entrarComSenha(@Valid @RequestBody SenhaLoginRequest pedido,
                                                        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                                        HttpServletRequest requisicao) {
        boolean lembrar = !Boolean.FALSE.equals(pedido.lembrar());
        String origem = LimiteDeRequisicoes.ipDeOrigem(requisicao, limites);
        return responder(servico.entrarComSenha(pedido.email(), pedido.senha(), lembrar, userAgent, origem),
                pedido.refreshNoCorpo());
    }

    /**
     * O site renova pelo cookie e recebe o refresh novo no cookie. O app nativo manda o refresh
     * no corpo e recebe o novo no corpo, sem cookie.
     */
    @Operation(summary = "Renovar a sessão com o refresh token")
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> renovar(@Valid @RequestBody(required = false) RefreshRequest corpo,
                                                 @CookieValue(name = COOKIE, required = false) String cookie,
                                                 @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        String doCorpo = doCorpo(corpo);
        if (doCorpo != null) {
            return ResponseEntity.ok(servico.renovar(doCorpo, userAgent));
        }
        if (cookie == null || cookie.isBlank()) {
            throw new UnauthorizedException("Sua sessao venceu. Entre novamente.");
        }
        return comCookie(servico.renovar(cookie, userAgent));
    }

    @Operation(summary = "Sair e encerrar a sessão do refresh token")
    @SecurityRequirements
    @PostMapping("/logout")
    public ResponseEntity<Void> sair(@Valid @RequestBody(required = false) RefreshRequest corpo,
                                     @CookieValue(name = COOKIE, required = false) String cookie) {
        String doCorpo = doCorpo(corpo);
        String refresh = doCorpo != null ? doCorpo : cookie;
        if (refresh != null && !refresh.isBlank()) {
            servico.sair(refresh);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    /** Login: o app nativo que pediu recebe o refresh no corpo; o site, no cookie. */
    private ResponseEntity<TokenResponse> responder(TokenResponse par, Boolean refreshNoCorpo) {
        return Boolean.TRUE.equals(refreshNoCorpo) ? ResponseEntity.ok(par) : comCookie(par);
    }

    private ResponseEntity<TokenResponse> comCookie(TokenResponse par) {
        // Sem "continuar conectado" o cookie e de sessao: some quando o navegador fecha.
        ResponseCookie cookie = cookie(par.refreshToken(), par.lembrar() ? validadeDaSessao : null);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(par.semRefresh());
    }

    private static String doCorpo(RefreshRequest corpo) {
        return corpo == null || Texto.vazio(corpo.refreshToken()) ? null : corpo.refreshToken();
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

    @Operation(summary = "Perfil de quem está logado")
    @GetMapping("/me")
    public UserResponse eu(@AuthUser CurrentUser usuario) {
        return servico.perfil(usuario.id());
    }

    /** Qualquer perfil escolhe a propria moeda; nao ha como mexer na de outra pessoa. */
    @Operation(summary = "Salvar as moedas de exibição e de conversão")
    @PutMapping("/me/preferences")
    public UserResponse preferencias(@Valid @RequestBody PreferencesRequest pedido,
                                     @AuthUser CurrentUser usuario) {
        return servico.salvarPreferencias(usuario.id(), pedido);
    }

    public record CompartilhamentoRequest(@NotNull Boolean mostrarTudoAoParceiro,
                                          @NotNull Boolean compartilharPorPadrao) {
    }

    /**
     * O que o parceiro ve do que a pessoa lanca: so o compartilhado ou tudo. Cada um decide
     * sobre os proprios dados; vale para o parceiro na proxima renovacao do token (ate 5 min).
     */
    @Operation(summary = "Escolher o que o parceiro vê")
    @PutMapping("/me/sharing")
    public UserResponse compartilhamento(@Valid @RequestBody CompartilhamentoRequest pedido,
                                         @AuthUser CurrentUser usuario) {
        return servico.escolherCompartilhamento(usuario.id(), pedido.mostrarTudoAoParceiro(),
                pedido.compartilharPorPadrao());
    }

    /**
     * Cria a senha de quem so entrava pelo Google, ou troca a atual. O cookie da sessao
     * deste aparelho (o caminho e /api/auth, entao ele vem junto) diz qual sessao fica; no app
     * nativo, o {@code refreshToken} do corpo.
     */
    @Operation(summary = "Criar ou trocar a senha")
    @PutMapping("/me/senha")
    public UserResponse definirSenha(@Valid @RequestBody DefinirSenhaRequest pedido,
                                     @AuthUser CurrentUser usuario,
                                     @CookieValue(name = COOKIE, required = false) String cookie) {
        String refresh = Texto.vazio(pedido.refreshToken()) ? cookie : pedido.refreshToken();
        return servico.definirSenha(usuario.id(), pedido.senhaAtual(), pedido.novaSenha(), refresh);
    }

    public record ModulosRequest(@NotNull(message = "Escolha os modulos.") @Size(max = 10) List<String> modulos) {
    }

    /** Liga e desliga as telas de estilo de vida no menu da propria pessoa. */
    @Operation(summary = "Ligar e desligar as telas de estilo de vida")
    @PutMapping("/me/modules")
    public UserResponse modulos(@Valid @RequestBody ModulosRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.escolherModulos(usuario.id(), pedido.modulos());
    }
}
