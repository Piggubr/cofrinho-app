package com.piggu.identity.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Guarda o CPF (e o documento de quem mora fora do Brasil) cifrado e calcula o indice
 * que impede duas contas com o mesmo documento.
 *
 * <p>Duas colunas: {@code cpf_cifrado} (AES-256-GCM, para devolver o numero na
 * exportacao dos dados) e {@code cpf_indice} (HMAC-SHA256, unico no banco). O indice
 * nao se reverte e, sem a chave, nao da para testar os CPFs possiveis contra ele, como
 * daria com um SHA-256 simples (sao so 10^9 combinacoes). Um dump do banco sozinho nao
 * entrega CPF de ninguem.</p>
 *
 * <p>A chave vem de {@code PIGGU_CPF_CHAVE} (32 bytes em base64: {@code openssl rand
 * -base64 32}). Em producao o identity recusa subir sem ela ou com a de desenvolvimento.
 * <strong>Trocar a chave depois quebra a leitura dos CPFs ja guardados</strong>: guarde-a
 * junto com a senha do backup.</p>
 */
@Component
public class CofreDeCpf {

    /** So para desenvolvimento e testes; publica de proposito. */
    static final String CHAVE_DE_DESENVOLVIMENTO = "cGlnZ3UtZGV2LWNwZi1uYW8tdXNlLWVtLXByb2R1Y2Fv";

    private static final int TAMANHO_DO_NONCE = 12;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final SecretKeySpec chaveDaCifra;
    private final SecretKeySpec chaveDoIndice;

    public CofreDeCpf(@Value("${piggu.cpf.chave:}") String chave, @Value("${piggu.ambiente:dev}") String ambiente) {
        boolean producao = "producao".equalsIgnoreCase(ambiente);
        if (chave == null || chave.isBlank()) {
            if (producao) {
                throw new IllegalStateException("Em producao defina PIGGU_CPF_CHAVE (gere com: openssl rand -base64 32).");
            }
            chave = CHAVE_DE_DESENVOLVIMENTO;
        }
        if (producao && CHAVE_DE_DESENVOLVIMENTO.equals(chave.trim())) {
            throw new IllegalStateException("PIGGU_CPF_CHAVE e a chave de desenvolvimento. Gere outra: openssl rand -base64 32");
        }
        byte[] mestra = Base64.getDecoder().decode(chave.trim());
        if (mestra.length < 32) {
            throw new IllegalStateException("PIGGU_CPF_CHAVE precisa de 32 bytes (openssl rand -base64 32).");
        }
        // Uma chave para cada uso, derivadas da mestra: o indice nunca reaproveita a chave da cifra.
        this.chaveDaCifra = new SecretKeySpec(hmac(mestra, "piggu-cpf-cifra"), "AES");
        this.chaveDoIndice = new SecretKeySpec(hmac(mestra, "piggu-cpf-indice"), "HmacSHA256");
    }

    /** Sempre o mesmo para o mesmo CPF: e o que a coluna unica compara. */
    public String indice(Cpf cpf) {
        return indiceDe(cpf.numero());
    }

    /**
     * Indice do documento de quem mora fora. Leva o tipo junto ("PT_NIF:123456789"):
     * o mesmo numero em paises diferentes e de pessoas diferentes.
     */
    public String indice(DocumentosDeIdentidade.Tipo tipo, String numero) {
        return indiceDe(tipo.name() + ":" + numero);
    }

    /** Nonce aleatorio + texto cifrado, em base64. */
    public String cifrar(Cpf cpf) {
        return cifrar(cpf.numero());
    }

    public Cpf decifrar(String guardado) {
        return new Cpf(decifrarTexto(guardado));
    }

    /** Nonce aleatorio + texto cifrado, em base64 (o CPF e os documentos de fora). */
    public String cifrar(String texto) {
        try {
            byte[] nonce = new byte[TAMANHO_DO_NONCE];
            ALEATORIO.nextBytes(nonce);
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.ENCRYPT_MODE, chaveDaCifra, new GCMParameterSpec(128, nonce));
            byte[] cifrado = cifra.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + cifrado.length)
                    .put(nonce).put(cifrado).array());
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("Nao foi possivel cifrar o documento", erro);
        }
    }

    public String decifrarTexto(String guardado) {
        try {
            byte[] tudo = Base64.getDecoder().decode(guardado);
            Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
            cifra.init(Cipher.DECRYPT_MODE, chaveDaCifra, new GCMParameterSpec(128, tudo, 0, TAMANHO_DO_NONCE));
            byte[] claro = cifra.doFinal(tudo, TAMANHO_DO_NONCE, tudo.length - TAMANHO_DO_NONCE);
            return new String(claro, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException erro) {
            throw new IllegalStateException("Nao foi possivel ler o documento guardado (a PIGGU_CPF_CHAVE mudou?)", erro);
        }
    }

    private String indiceDe(String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(chaveDoIndice);
            return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("Nao foi possivel calcular o indice do documento", erro);
        }
    }

    private static byte[] hmac(byte[] chave, String rotulo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chave, "HmacSHA256"));
            return mac.doFinal(rotulo.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException(erro);
        }
    }
}
