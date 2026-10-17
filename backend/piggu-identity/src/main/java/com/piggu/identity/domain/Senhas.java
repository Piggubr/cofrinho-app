package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Regra e guarda das senhas de quem entra por e-mail.
 *
 * <p>BCrypt com custo 12: cada conferencia leva uma fracao de segundo, o que nao pesa
 * para quem entra e encarece muito quem tenta adivinhar. O BCrypt so le os primeiros
 * 72 bytes, por isso a senha nao pode passar disso (senao o fim dela seria ignorado
 * sem a pessoa saber).</p>
 */
@Component
public class Senhas {

    static final int MINIMO = 8;
    static final int MAXIMO_EM_BYTES = 72;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    /**
     * Hash de uma senha qualquer, para conferir mesmo quando a conta nao existe: assim
     * o tempo da resposta nao entrega quais e-mails tem conta.
     */
    private final String hashDeEnchimento = encoder.encode("piggu-sem-conta-" + System.nanoTime());

    /** Recusa senha curta, longa demais, sem letra ou sem numero, ou igual ao e-mail. */
    public void validar(String senha, String email) {
        if (senha == null || senha.length() < MINIMO) {
            throw new BusinessException("A senha precisa ter pelo menos " + MINIMO + " caracteres.",
                    HttpStatus.UNPROCESSABLE_CONTENT, "SENHA_FRACA");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > MAXIMO_EM_BYTES) {
            throw new BusinessException("A senha pode ter no maximo " + MAXIMO_EM_BYTES + " caracteres.",
                    HttpStatus.UNPROCESSABLE_CONTENT, "SENHA_FRACA");
        }
        if (senha.chars().noneMatch(Character::isLetter) || senha.chars().noneMatch(Character::isDigit)) {
            throw new BusinessException("Use letras e numeros na senha.",
                    HttpStatus.UNPROCESSABLE_CONTENT, "SENHA_FRACA");
        }
        if (email != null && senha.trim().equalsIgnoreCase(email.trim())) {
            throw new BusinessException("A senha nao pode ser o seu e-mail.",
                    HttpStatus.UNPROCESSABLE_CONTENT, "SENHA_FRACA");
        }
    }

    public String hash(String senha) {
        return encoder.encode(senha);
    }

    /** @param hash nulo quando a conta nao existe ou nao tem senha; a conferencia roda igual */
    public boolean confere(String senha, String hash) {
        if (senha == null || senha.getBytes(StandardCharsets.UTF_8).length > MAXIMO_EM_BYTES) {
            encoder.matches("", hashDeEnchimento);
            return false;
        }
        if (hash == null) {
            encoder.matches(senha, hashDeEnchimento);
            return false;
        }
        return encoder.matches(senha, hash);
    }
}
