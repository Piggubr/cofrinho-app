package com.piggu.identity.domain;

import com.piggu.common.web.Moedas;
import com.piggu.identity.api.dto.PreferencesRequest;

import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.security.PigguRole;
import com.piggu.common.web.Texto;
import com.piggu.identity.api.dto.TokenResponse;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.google.GoogleIdTokenVerifier;
import com.piggu.identity.google.GoogleProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Entrada, renovacao e saida da conta.
 *
 * <p>Reune o que no Code.gs estava espalhado entre {@code validarUsuario_},
 * {@code criarSessao_}, {@code validarSessao_}, {@code obterOuCriarUsuario_} e
 * {@code limparSessoesExpiradas_}.</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final GoogleIdTokenVerifier verificador;
    private final UserAccountRepository usuarios;
    private final AuthorizedEmailRepository autorizados;
    private final RefreshSessionRepository sessoes;
    private final SessionRevoker revogador;
    private final TokenService tokens;

    public AuthService(GoogleIdTokenVerifier verificador,
                       UserAccountRepository usuarios,
                       AuthorizedEmailRepository autorizados,
                       RefreshSessionRepository sessoes,
                       SessionRevoker revogador,
                       TokenService tokens) {
        this.verificador = verificador;
        this.usuarios = usuarios;
        this.autorizados = autorizados;
        this.sessoes = sessoes;
        this.revogador = revogador;
        this.tokens = tokens;
    }

    /**
     * Troca o ID token do Google por um par de tokens do Piggu.
     * Cria a conta no primeiro acesso, desde que o e-mail esteja liberado.
     */
    @Transactional
    public TokenResponse entrarComGoogle(String idToken, String userAgent) {
        GoogleProfile perfil = verificador.verificar(idToken);
        UserAccount conta = obterOuCriar(perfil);

        if (!conta.isActive()) {
            throw new ForbiddenException("Este e-mail nao esta autorizado.");
        }

        conta.atualizarPerfilGoogle(perfil.name(), perfil.givenName(), perfil.picture());
        usuarios.save(conta);

        return emitirPar(conta, userAgent);
    }

    /** Renova o acesso e rotaciona a sessao longa: o refresh usado e' descartado. */
    @Transactional
    public TokenResponse renovar(String refreshToken, String userAgent) {
        RefreshSession sessao = sessoes.findByTokenHash(tokens.hash(refreshToken))
                .orElseThrow(() -> new UnauthorizedException("Sua sessao venceu. Entre novamente com Google."));

        if (!sessao.estaValida()) {
            // Revogacao em transacao propria: a excecao abaixo desfaz esta transacao,
            // e um DELETE feito aqui dentro seria desfeito junto.
            revogador.revogar(sessao.getId());
            throw new UnauthorizedException("Sua sessao venceu. Entre novamente com Google.");
        }

        UserAccount conta = usuarios.findById(sessao.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));

        if (!conta.isActive()) {
            revogador.revogarTodasDe(conta.getId());
            throw new ForbiddenException("Este e-mail nao esta autorizado.");
        }

        sessoes.delete(sessao);
        return emitirPar(conta, userAgent);
    }

    @Transactional
    public void sair(String refreshToken) {
        sessoes.findByTokenHash(tokens.hash(refreshToken)).ifPresent(sessoes::delete);
    }

    @Transactional(readOnly = true)
    public UserResponse perfil(java.util.UUID usuarioId) {
        return usuarios.findById(usuarioId)
                .map(UserResponse::de)
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
    }

    @Transactional
    public UserResponse salvarPreferencias(java.util.UUID usuarioId, PreferencesRequest pedido) {
        UserAccount conta = usuarios.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
        conta.alterarPreferencias(
                Moedas.validar(pedido.moeda()),
                Moedas.validar(pedido.moedaConversao()),
                pedido.mostrarCotacao());
        return UserResponse.de(conta);
    }

    private TokenResponse emitirPar(UserAccount conta, String userAgent) {
        limparSessoesVencidas();

        String refresh = tokens.gerarRefreshToken();
        sessoes.save(new RefreshSession(
                conta.getId(),
                tokens.hash(refresh),
                Texto.limitar(userAgent, 300),
                tokens.expiracaoDaSessao()
        ));

        return new TokenResponse(
                tokens.gerarAccessToken(conta),
                refresh,
                tokens.segundosDeAcesso(),
                UserResponse.de(conta)
        );
    }

    private UserAccount obterOuCriar(GoogleProfile perfil) {
        return usuarios.findByEmail(perfil.email()).orElseGet(() -> {
            PigguRole role = autorizados.findByEmail(perfil.email())
                    .map(AuthorizedEmail::getRole)
                    .orElseThrow(() -> new ForbiddenException("Este e-mail nao esta autorizado."));
            UserAccount nova = usuarios.save(new UserAccount(perfil.email(), role));
            // Id, nunca o e-mail: log e copia de dado pessoal que ninguem apaga.
            log.info("Conta criada id={} perfil={}", nova.getId(), role);
            return nova;
        });
    }

    private void limparSessoesVencidas() {
        try {
            int removidas = sessoes.apagarExpiradas(Instant.now());
            if (removidas > 0) {
                log.debug("{} sessoes vencidas removidas", removidas);
            }
        } catch (RuntimeException erro) {
            log.warn("Nao foi possivel limpar sessoes vencidas agora", erro);
        }
    }
}
