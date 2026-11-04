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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    /** Um aviso de cadastro repetido por conta neste intervalo: o cadastro nao vira disparador. */
    static final Duration INTERVALO_DO_AVISO = Duration.ofHours(6);
    private static final int AVISOS_ANTES_DE_LIMPAR = 10_000;

    /** ponytail: em memoria, por instancia; com mais de uma replica vira Redis. */
    private final Map<UUID, Instant> ultimoAviso = new ConcurrentHashMap<>();

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
        depoisDoCommit(() -> email.enviar(conta.getEmail(), "Piggu: confirme seu e-mail",
                "Para confirmar seu e-mail no Piggu, abra o link abaixo. Ele vale por 24 horas.\n\n" + link
                        + "\n\nSe nao foi voce quem criou a conta, ignore este e-mail."));
    }

    /**
     * Alguem tentou cadastrar um e-mail que ja tem conta. A resposta do cadastro e a mesma
     * de um cadastro novo; quem fica sabendo e so o dono do e-mail, uma vez a cada
     * {@link #INTERVALO_DO_AVISO} (repetir o cadastro em loop nao enche a caixa dele).
     */
    public void avisarCadastroRepetido(UserAccount conta) {
        Instant agora = Instant.now();
        boolean[] mandar = {false};
        ultimoAviso.compute(conta.getId(), (id, anterior) -> {
            if (anterior != null && anterior.plus(INTERVALO_DO_AVISO).isAfter(agora)) {
                return anterior;
            }
            mandar[0] = true;
            return agora;
        });
        if (!mandar[0]) {
            log.info("Aviso de cadastro repetido ja enviado ha pouco: conta={}", conta.getId());
            return;
        }
        if (ultimoAviso.size() > AVISOS_ANTES_DE_LIMPAR) {
            ultimoAviso.values().removeIf(quando -> !quando.plus(INTERVALO_DO_AVISO).isAfter(agora));
        }
        if (!email.ligado()) {
            log.info("E-mail desligado; aviso de cadastro repetido nao enviado: conta={}", conta.getId());
            return;
        }
        depoisDoCommit(() -> email.enviar(conta.getEmail(), "Piggu: tentativa de criar conta com seu e-mail",
                "Alguem tentou criar uma conta no Piggu com este e-mail, que ja tem conta.\n\n"
                        + "Se foi voce, entre em " + site + "/entrar com sua senha ou pelo Google. "
                        + "Se nao foi, ignore este e-mail: nada mudou na sua conta."));
    }

    /**
     * Manda depois do commit, numa thread a parte: o link so sai se a conta foi gravada, a
     * conexao com o banco nao espera o provedor de e-mail (ate 30 s) e o e-mail que tem conta
     * nao demora mais para responder do que o que nao tem.
     */
    static void depoisDoCommit(Runnable envio) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            Thread.startVirtualThread(envio);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Thread.startVirtualThread(envio);
            }
        });
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

    /**
     * Confirma o e-mail de uma conta com senha so com a sessao da propria conta aberta (D-152).
     * O link prova o e-mail, mas nao quem escolheu a senha: sem isso, quem cadastrou o e-mail de
     * outra pessoa seguiria entrando na conta depois que o dono clicasse.
     *
     * @param quem conta da sessao que abriu o link, ou null sem sessao
     */
    @Transactional
    public void confirmar(String token, UUID quem) {
        UserAccount conta = token == null || token.isBlank() ? null
                : usuarios.findByEmailTokenHash(tokens.hash(token.trim())).orElse(null);
        if (conta == null || !conta.linkDeEmailValido(Instant.now())) {
            throw new BusinessException("Este link de confirmacao venceu ou ja foi usado. Peca outro no Perfil.",
                    HttpStatus.UNPROCESSABLE_CONTENT, "LINK_INVALIDO");
        }
        if (conta.temSenha() && !conta.getId().equals(quem)) {
            throw new BusinessException("Para confirmar, entre no Piggu com a conta deste e-mail e abra o link de novo.",
                    HttpStatus.FORBIDDEN, "ENTRE_PARA_CONFIRMAR");
        }
        conta.confirmarEmail();
        usuarios.save(conta);
        log.info("E-mail confirmado por link: conta={}", conta.getId());
    }
}
