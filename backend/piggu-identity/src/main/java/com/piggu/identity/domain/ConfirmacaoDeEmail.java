package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.identity.avisos.CanalDeEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
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
    static final Duration INTERVALO = Duration.ofMinutes(1);

    private final UserAccountRepository usuarios;
    private final TokenService tokens;
    private final CanalDeEmail email;
    private final String site;
    private final boolean producao;

    public ConfirmacaoDeEmail(UserAccountRepository usuarios, TokenService tokens,
                              CanalDeEmail email, @Value("${SITE_URL:http://localhost:4200}") String site,
                              @Value("${piggu.ambiente:dev}") String ambiente) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.email = email;
        this.site = site.endsWith("/") ? site.substring(0, site.length() - 1) : site;
        this.producao = "producao".equals(ambiente);
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
            // O link confirma a conta: em producao nao vai para o log, onde mais gente le.
            if (producao) {
                log.warn("E-mail desligado em producao; link de confirmacao nao enviado: conta={}", conta.getId());
            } else {
                log.info("E-mail desligado; link de confirmacao da conta {}: {}", conta.getId(), link);
            }
            return;
        }
        email.enviar(conta.getEmail(), "Piggu: confirme seu e-mail",
                "Para confirmar seu e-mail no Piggu, abra o link abaixo. Ele vale por 24 horas.\n\n" + link
                        + "\n\nSe nao foi voce quem criou a conta, ignore este e-mail.");
    }

    /** Um link por minuto, no maximo: o botao nao vira disparador de e-mail. */
    @Transactional
    public void reenviar(UUID usuarioId) {
        UserAccount conta = usuarios.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
        if (conta.linkDeEmailValido(Instant.now().plus(VALIDADE).minus(INTERVALO))) {
            throw new BusinessException("Acabamos de mandar um link. Espere um minuto para pedir outro.",
                    HttpStatus.TOO_MANY_REQUESTS, "LINK_RECENTE");
        }
        enviar(conta);
    }

    @Transactional
    public void confirmar(String token) {
        UserAccount conta = token == null || token.isBlank() ? null
                : usuarios.findByEmailTokenHash(tokens.hash(token.trim())).orElse(null);
        if (conta == null || !conta.linkDeEmailValido(Instant.now())) {
            throw new BusinessException("Este link de confirmacao venceu ou ja foi usado. Peca outro no Perfil.",
                    HttpStatus.UNPROCESSABLE_CONTENT, "LINK_INVALIDO");
        }
        conta.confirmarEmail();
        usuarios.save(conta);
        log.info("E-mail confirmado por link: conta={}", conta.getId());
    }
}
