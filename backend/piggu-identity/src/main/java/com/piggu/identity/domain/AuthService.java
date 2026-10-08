package com.piggu.identity.domain;

import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.security.PigguRole;
import com.piggu.common.web.Moedas;
import com.piggu.common.web.Texto;
import com.piggu.identity.api.dto.PreferencesRequest;
import com.piggu.identity.api.dto.TokenResponse;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.google.GoogleIdTokenVerifier;
import com.piggu.identity.google.GoogleProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final FamiliaService familias;
    private final RefreshSessionRepository sessoes;
    private final SessionRevoker revogador;
    private final TokenService tokens;
    private final Set<String> emailsDeAdmin;

    public AuthService(GoogleIdTokenVerifier verificador,
                       UserAccountRepository usuarios,
                       FamiliaService familias,
                       RefreshSessionRepository sessoes,
                       SessionRevoker revogador,
                       TokenService tokens,
                       @Value("${piggu.admin-emails:}") List<String> emailsDeAdmin) {
        this.verificador = verificador;
        this.usuarios = usuarios;
        this.familias = familias;
        this.sessoes = sessoes;
        this.revogador = revogador;
        this.tokens = tokens;
        this.emailsDeAdmin = emailsDeAdmin.stream().map(Texto::email).filter(email -> !email.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Troca o ID token do Google por um par de tokens do Piggu.
     * Cria a conta (e a familia, ou entra na que convidou) no primeiro acesso.
     */
    @Transactional
    public TokenResponse entrarComGoogle(String idToken, String versaoDosTermos, String userAgent) {
        return entrarComGoogle(idToken, versaoDosTermos, true, userAgent);
    }

    /** @param lembrar continuar conectado: o cookie do refresh sobrevive a fechar o navegador */
    @Transactional
    public TokenResponse entrarComGoogle(String idToken, String versaoDosTermos, boolean lembrar, String userAgent) {
        GoogleProfile perfil = verificador.verificar(idToken);
        UserAccount conta = usuarios.findByEmail(perfil.email())
                .orElseGet(() -> familias.criarConta(perfil.email(), perfil.givenName(), versaoDosTermos));

        if (!conta.isActive()) {
            log.warn("Login recusado: conta desativada id={}", conta.getId());
            throw new ForbiddenException("Esta conta foi desativada.");
        }

        conta.atualizarPerfilGoogle(perfil.name(), perfil.givenName(), perfil.picture());
        // Promove, nunca rebaixa: tirar alguem da lista nao derruba um ADMIN por engano.
        if (emailsDeAdmin.contains(conta.getEmail()) && conta.getRole() != PigguRole.ADMIN) {
            conta.setRole(PigguRole.ADMIN);
            log.info("Conta promovida a ADMIN pela PIGGU_ADMIN_EMAILS: conta={}", conta.getId());
        }
        usuarios.save(conta);
        log.info("Login: conta={}", conta.getId());

        return emitirPar(conta, userAgent, lembrar);
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
            log.warn("Renovacao recusada: conta desativada id={}", conta.getId());
            revogador.revogarTodasDe(conta.getId());
            throw new ForbiddenException("Esta conta foi desativada.");
        }

        sessoes.delete(sessao);
        return emitirPar(conta, userAgent, sessao.isRemember());
    }

    @Transactional
    public void sair(String refreshToken) {
        sessoes.findByTokenHash(tokens.hash(refreshToken)).ifPresent(sessoes::delete);
    }

    @Transactional(readOnly = true)
    public UserResponse perfil(java.util.UUID usuarioId) {
        return usuarios.findById(usuarioId)
                .map(conta -> UserResponse.de(conta, familias.daConta(conta)))
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
    }

    @Transactional
    public UserResponse salvarPreferencias(java.util.UUID usuarioId, PreferencesRequest pedido) {
        String moeda = Moedas.validar(pedido.moeda());
        String conversao = Moedas.validar(pedido.moedaConversao());
        UserAccount conta = usuarios.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
        conta.alterarPreferencias(moeda, conversao, pedido.mostrarCotacao());
        return UserResponse.de(conta, familias.daConta(conta));
    }

    private TokenResponse emitirPar(UserAccount conta, String userAgent, boolean lembrar) {
        limparSessoesVencidas();

        String refresh = tokens.gerarRefreshToken();
        sessoes.save(new RefreshSession(
                conta.getId(),
                tokens.hash(refresh),
                Texto.limitar(userAgent, 300),
                tokens.expiracaoDaSessao(),
                lembrar
        ));

        Household familia = familias.daConta(conta);
        return new TokenResponse(
                tokens.gerarAccessToken(conta, familia),
                refresh,
                tokens.segundosDeAcesso(),
                UserResponse.de(conta, familia),
                lembrar
        );
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
