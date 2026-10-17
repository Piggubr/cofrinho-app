package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;

/**
 * CPF conferido pelos digitos verificadores (algoritmo da Receita). So confere a
 * conta: nao consulta a Receita, entao um CPF valido de outra pessoa passa. O que
 * impede duas contas da mesma pessoa e o CPF ser unico entre as contas.
 *
 * @param numero os 11 digitos, sem pontuacao
 */
public record Cpf(String numero) {

    public Cpf {
        if (!valido(numero)) {
            throw new BusinessException("CPF inválido. Confira os números.");
        }
    }

    /** Aceita com ou sem pontuacao ("123.456.789-09" ou "12345678909"). */
    public static Cpf de(String digitado) {
        if (digitado == null) {
            throw new BusinessException("Informe o CPF.");
        }
        String digitos = digitado.replaceAll("[.\\-\\s]", "");
        if (!digitos.matches("\\d{11}")) {
            throw new BusinessException("O CPF tem 11 números.");
        }
        return new Cpf(digitos);
    }

    static boolean valido(String numero) {
        if (numero == null || !numero.matches("\\d{11}")) {
            return false;
        }
        // 000.000.000-00, 111.111.111-11... passam no calculo, mas nao existem.
        if (numero.chars().distinct().count() == 1) {
            return false;
        }
        return digito(numero, 9) == numero.charAt(9) - '0' && digito(numero, 10) == numero.charAt(10) - '0';
    }

    /** Digito verificador na posicao {@code posicao} (9 ou 10), a partir dos anteriores. */
    private static int digito(String numero, int posicao) {
        int soma = 0;
        for (int i = 0; i < posicao; i++) {
            soma += (numero.charAt(i) - '0') * (posicao + 1 - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /** "***.456.789-**": o bastante para a pessoa reconhecer o proprio CPF na tela. */
    public String mascarado() {
        return "***." + numero.substring(3, 6) + "." + numero.substring(6, 9) + "-**";
    }

    /** "123.456.789-09", para a exportacao dos dados. */
    public String formatado() {
        return numero.substring(0, 3) + "." + numero.substring(3, 6) + "." + numero.substring(6, 9)
                + "-" + numero.substring(9);
    }

    @Override
    public String toString() {
        // Nunca o numero inteiro em log ou mensagem de erro.
        return mascarado();
    }
}
