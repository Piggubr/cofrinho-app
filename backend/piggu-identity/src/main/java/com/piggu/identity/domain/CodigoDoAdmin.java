package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Segunda etapa das rotas de ADMIN (D-182): o codigo de 6 digitos do app autenticador
 * (TOTP, RFC 6238, SHA-1 e 30 segundos), no cabecalho {@code X-Codigo-Admin}.
 *
 * <p>O segredo vem so de {@code PIGGU_ADMIN_TOTP}, em base32, o formato que o app
 * autenticador le. Sem ele as rotas recusam sempre. Aceita o passo anterior e o seguinte
 * (relogio adiantado ou atrasado), nunca o mesmo passo duas vezes, e depois de 5 erros
 * seguidos recusa tudo por 15 minutos.</p>
 */
@Component
public class CodigoDoAdmin {

    public static final String CABECALHO = "X-Codigo-Admin";

    private static final long PASSO_EM_SEGUNDOS = 30;
    private static final int ERROS_ATE_BLOQUEAR = 5;
    private static final Duration BLOQUEIO = Duration.ofMinutes(15);

    private final byte[] chave;
    private long ultimoPassoAceito = Long.MIN_VALUE;
    private int errosSeguidos;
    private Instant bloqueadoAte = Instant.MIN;

    public CodigoDoAdmin(@Value("${piggu.admin-totp:}") String segredo) {
        this.chave = segredo == null || segredo.isBlank() ? null : base32(segredo);
    }

    /** Recusa com 403 (codigo CODIGO_DO_ADMIN) se o codigo nao confere. */
    public void exigir(String codigo) {
        if (!confere(codigo, Instant.now())) {
            throw new BusinessException("Confirme com o código do app autenticador.", HttpStatus.FORBIDDEN,
                    "CODIGO_DO_ADMIN");
        }
    }

    synchronized boolean confere(String codigo, Instant agora) {
        if (chave == null || agora.isBefore(bloqueadoAte)) {
            return false;
        }
        long passo = agora.getEpochSecond() / PASSO_EM_SEGUNDOS;
        if (codigo != null && codigo.matches("\\d{6}")) {
            for (long p = passo - 1; p <= passo + 1; p++) {
                boolean igual = MessageDigest.isEqual(gerar(chave, p).getBytes(), codigo.getBytes());
                if (igual && p > ultimoPassoAceito) {
                    ultimoPassoAceito = p;
                    errosSeguidos = 0;
                    return true;
                }
            }
        }
        if (++errosSeguidos >= ERROS_ATE_BLOQUEAR) {
            errosSeguidos = 0;
            bloqueadoAte = agora.plus(BLOQUEIO);
        }
        return false;
    }

    /** O codigo do segredo (base32) naquele instante, como o app autenticador mostra. */
    public static String gerar(String segredo, Instant quando) {
        return gerar(base32(segredo), quando.getEpochSecond() / PASSO_EM_SEGUNDOS);
    }

    static String gerar(byte[] chave, long passo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1"); // NOSONAR: TOTP (RFC 6238); HMAC nao depende de colisao
            mac.init(new SecretKeySpec(chave, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(passo).array());
            int i = h[h.length - 1] & 0x0f;
            int numero = ((h[i] & 0x7f) << 24) | ((h[i + 1] & 0xff) << 16) | ((h[i + 2] & 0xff) << 8) | (h[i + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", numero % 1_000_000);
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("HmacSHA1 indisponivel nesta JVM.", erro);
        }
    }

    /** Base32 da RFC 4648, sem diferenciar maiusculas e ignorando espacos e o '=' do fim. */
    static byte[] base32(String texto) {
        String limpo = texto.replaceAll("[\\s=]", "").toUpperCase(Locale.ROOT);
        ByteBuffer saida = ByteBuffer.allocate(limpo.length() * 5 / 8);
        int bits = 0;
        int acumulado = 0;
        for (char c : limpo.toCharArray()) {
            int valor = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
            if (valor < 0) {
                throw new IllegalArgumentException("PIGGU_ADMIN_TOTP nao e base32.");
            }
            acumulado = (acumulado << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                saida.put((byte) (acumulado >> (bits - 8)));
                bits -= 8;
            }
        }
        return saida.array();
    }
}
