package com.piggu.identity.google;

import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.web.Texto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login com Google no app instalado (iOS e Android), que abre o site dentro de um WebView.
 *
 * <p>O Google nao deixa fazer login dentro de WebView, e a janela do botao do Google abria no
 * Safari (ou no Chrome) sem ter para onde voltar: escolhida a conta, a tela ficava branca. Aqui o
 * login sai do app e volta, como pede a RFC 8252:</p>
 * <ol>
 *   <li>o app sorteia um verificador, guarda e abre o Google no navegador do celular com o
 *       desafio (SHA-256 do verificador) como nonce e state;</li>
 *   <li>o Google devolve o ID token por POST em /api/auth/google/app; o token e conferido, com o
 *       nonce, e fica guardado aqui sob um codigo de uso unico;</li>
 *   <li>a pagina de resposta abre o app por piggu://entrar?google=codigo;</li>
 *   <li>o app troca o codigo e o verificador pelo ID token e entra como no site (/api/auth/google).</li>
 * </ol>
 *
 * <p>So quem tem o verificador, que nunca sai do app, troca o codigo: outro app que capture o
 * piggu:// nao entra, e um token de outra conta enfiado no retorno nao serve no aparelho de
 * ninguem. ponytail: guardado em memoria, como os limites de chamada. Com mais de um identity,
 * mover para o banco ou para o Redis.</p>
 */
@Component
public class LoginPeloNavegador {

    /** Tempo para a pessoa voltar ao app depois de escolher a conta. */
    static final Duration VALIDADE = Duration.ofMinutes(5);
    private static final int MAXIMO_GUARDADO = 1_000;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final GoogleIdTokenVerifier google;
    private final Clock relogio;
    private final Map<String, Pendente> pendentes = new ConcurrentHashMap<>();

    @Autowired
    public LoginPeloNavegador(GoogleIdTokenVerifier google) {
        this(google, Clock.systemUTC());
    }

    LoginPeloNavegador(GoogleIdTokenVerifier google, Clock relogio) {
        this.google = google;
        this.relogio = relogio;
    }

    /**
     * Confere o token que o Google mandou e guarda ate o app vir buscar.
     *
     * @param desafio o state da volta, que o app tambem mandou como nonce
     * @return o codigo de uso unico que vai no link piggu://
     */
    public String guardar(String idToken, String desafio) {
        google.verificar(idToken, desafio);

        long agora = relogio.millis();
        pendentes.values().removeIf(pendente -> pendente.venceu(agora));
        if (pendentes.size() >= MAXIMO_GUARDADO) {
            throw new UnauthorizedException("Muitos logins ao mesmo tempo. Tente de novo em instantes.");
        }

        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String codigo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pendentes.put(codigo, new Pendente(idToken, desafio, agora + VALIDADE.toMillis()));
        return codigo;
    }

    /**
     * Devolve o ID token ao app que comecou o login. O codigo vale uma vez so, certo ou errado.
     *
     * @param verificador o segredo que o app sorteou; o SHA-256 dele tem de bater com o desafio
     */
    public String trocar(String codigo, String verificador) {
        Pendente pendente = Texto.vazio(codigo) ? null : pendentes.remove(codigo);
        if (pendente == null || pendente.venceu(relogio.millis()) || Texto.vazio(verificador)
                || !MessageDigest.isEqual(desafioDe(verificador).getBytes(StandardCharsets.US_ASCII),
                        pendente.desafio().getBytes(StandardCharsets.US_ASCII))) {
            throw new UnauthorizedException("O login com Google venceu. Tente de novo.");
        }
        return pendente.idToken();
    }

    /** O mesmo calculo do app: SHA-256 em base64url sem "=", como o S256 do PKCE. */
    static String desafioDe(String verificador) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(verificador.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(resumo);
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException(erro);
        }
    }

    private record Pendente(String idToken, String desafio, long venceEm) {
        boolean venceu(long agora) {
            return agora >= venceEm;
        }
    }
}
