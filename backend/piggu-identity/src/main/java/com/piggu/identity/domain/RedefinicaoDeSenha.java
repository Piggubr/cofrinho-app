package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.web.Texto;
import com.piggu.identity.avisos.CanalDeEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * "Esqueci minha senha": um link de 1 hora no e-mail da conta. Do link o banco so guarda
 * o SHA-256 e um pedido novo invalida o anterior, como em {@link ConfirmacaoDeEmail}.
 *
 * <p>O pedido responde igual com ou sem conta, para nao dizer a ninguem quais e-mails
 * usam o Piggu. Usar o link troca a senha, confirma o e-mail e derruba todas as sessoes:
 * quem pediu pode estar tirando alguem de dentro da conta.</p>
 */
@Service
public class RedefinicaoDeSenha {

    private static final Logger log = LoggerFactory.getLogger(RedefinicaoDeSenha.class);

    static final Duration VALIDADE = Duration.ofHours(1);
    static final Duration INTERVALO = Duration.ofMinutes(1);

    private final UserAccountRepository usuarios;
    private final RefreshSessionRepository sessoes;
    private final TokenService tokens;
    private final Senhas senhas;
    private final CanalDeEmail email;
    private final FamiliaService familias;
    private final String site;
    private final boolean producao;

    public RedefinicaoDeSenha(UserAccountRepository usuarios, RefreshSessionRepository sessoes,
                              TokenService tokens, Senhas senhas, CanalDeEmail email, FamiliaService familias,
                              @Value("${SITE_URL:http://localhost:4200}") String site,
                              @Value("${piggu.ambiente:dev}") String ambiente) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.tokens = tokens;
        this.senhas = senhas;
        this.email = email;
        this.familias = familias;
        this.site = site.endsWith("/") ? site.substring(0, site.length() - 1) : site;
        this.producao = "producao".equals(ambiente);
    }

    /**
     * Manda o link se o e-mail tem conta ativa. Um link por minuto por conta, no maximo; o
     * pedido a mais e ignorado em silencio, porque um erro diria que a conta existe.
     */
    @Transactional
    public void pedir(String emailBruto) {
        UserAccount conta = usuarios.findByEmail(Texto.email(emailBruto)).orElse(null);
        if (conta == null || !conta.isActive()) {
            return;
        }
        Instant agora = Instant.now();
        if (conta.linkDeSenhaValido(agora.plus(VALIDADE).minus(INTERVALO))) {
            log.info("Pedido de nova senha ignorado: link recente, conta={}", conta.getId());
            return;
        }
        String token = tokens.gerarRefreshToken();
        conta.pedirRedefinicaoDeSenha(tokens.hash(token), agora.plus(VALIDADE));
        usuarios.save(conta);
        String link = site + "/redefinir-senha?token=" + token;
        if (!email.ligado()) {
            // O link troca a senha da conta: em producao nao vai para o log, onde mais gente le.
            if (producao) {
                log.warn("E-mail desligado em producao; link de nova senha nao enviado: conta={}", conta.getId());
            } else {
                log.info("E-mail desligado; link de nova senha da conta {}: {}", conta.getId(), link);
            }
            return;
        }
        email.enviar(conta.getEmail(), "Piggu: crie uma senha nova",
                "Recebemos um pedido para criar uma senha nova no Piggu. Abra o link abaixo; ele vale por 1 hora.\n\n"
                        + link
                        + "\n\nSe nao foi voce quem pediu, ignore este e-mail: sua senha continua a mesma.");
        log.info("Link de nova senha enviado: conta={}", conta.getId());
    }

    /** Troca a senha pelo link; todas as sessoes abertas da conta caem. */
    @Transactional
    public void redefinir(String token, String novaSenha) {
        UserAccount conta = token == null || token.isBlank() ? null
                : usuarios.findByPasswordResetHash(tokens.hash(token.trim())).orElse(null);
        if (conta == null || !conta.linkDeSenhaValido(Instant.now())) {
            throw new BusinessException("Este link venceu ou ja foi usado. Peca outro em \"Esqueci minha senha\".",
                    HttpStatus.UNPROCESSABLE_CONTENT, "LINK_INVALIDO");
        }
        if (!conta.isActive()) {
            throw new ForbiddenException("Esta conta foi desativada.");
        }
        senhas.validar(novaSenha, conta.getEmail());

        if (!conta.isEmailVerified()) {
            // O link prova o e-mail de uma conta cadastrada sem prova: como no primeiro login Google.
            familias.devolverAoDono(conta);
        }
        conta.redefinirSenha(senhas.hash(novaSenha));
        usuarios.save(conta);
        sessoes.apagarPorUsuario(conta.getId());
        log.info("Senha redefinida pelo link do e-mail: conta={}", conta.getId());
    }
}
