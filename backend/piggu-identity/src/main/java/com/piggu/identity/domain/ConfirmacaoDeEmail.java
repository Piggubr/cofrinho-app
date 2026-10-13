package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.avisos.CanalDeEmail;
import com.piggu.identity.avisos.Mensagem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Prova do e-mail de quem cadastrou por senha: um link de 24 horas. Do link o banco so
 * guarda o SHA-256; um pedido novo invalida o anterior. Sem provedor de e-mail
 * configurado (dev), o link vai para o log.
 */
@Service
public class ConfirmacaoDeEmail {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoDeEmail.class);

    static final Duration VALIDADE = Duration.ofHours(24);

    private final UserAccountRepository usuarios;
    private final FamiliaService familias;
    private final TokenService tokens;
    private final CanalDeEmail email;
    private final String site;

    public ConfirmacaoDeEmail(UserAccountRepository usuarios, FamiliaService familias, TokenService tokens,
                              CanalDeEmail email, @Value("${SITE_URL:http://localhost:4200}") String site) {
        this.usuarios = usuarios;
        this.familias = familias;
        this.tokens = tokens;
        this.email = email;
        this.site = site.endsWith("/") ? site.substring(0, site.length() - 1) : site;
    }

    /** Gera o link e manda; e-mail ja confirmado nao recebe nada. */
    @Transactional
    public void enviar(UserAccount conta) {
        if (conta.isEmailVerified()) {
            return;
        }
        String token = tokens.gerarRefreshToken();
        conta.pedirConfirmacaoDeEmail(tokens.hash(token), Instant.now().plus(VALIDADE));
        usuarios.save(conta);
        String link = site + "/confirmar-email?token=" + token;
        if (!email.ligado()) {
            log.info("E-mail desligado; link de confirmacao da conta {}: {}", conta.getId(), link);
            return;
        }
        email.enviar(conta.getEmail(), new Mensagem("Piggu: confirme seu e-mail",
                List.of("Para confirmar seu e-mail no Piggu, abra o link (vale 24 horas): " + link)));
    }

    @Transactional
    public void reenviar(UUID usuarioId) {
        enviar(usuarios.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente.")));
    }

    @Transactional
    public UserResponse confirmar(String token) {
        UserAccount conta = token == null || token.isBlank() ? null
                : usuarios.findByEmailTokenHash(tokens.hash(token.trim())).orElse(null);
        if (conta == null || !conta.linkDeEmailValido(Instant.now())) {
            throw new BusinessException("Este link de confirmacao venceu ou ja foi usado. Peca outro no Perfil.",
                    HttpStatus.UNPROCESSABLE_CONTENT, "LINK_INVALIDO");
        }
        conta.confirmarEmail();
        usuarios.save(conta);
        log.info("E-mail confirmado por link: conta={}", conta.getId());
        return UserResponse.de(conta, familias.daConta(conta));
    }
}
