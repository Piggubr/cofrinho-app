package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Onde a pessoa mora e qual documento o Piggu pede la para manter uma conta por pessoa.
 *
 * <p>Nao existe um identificador mundial. No Brasil e o CPF ({@link Cpf}). Fora, so os
 * paises com um numero nacional de identidade de formato conhecido, que da para conferir
 * sem consultar ninguem e que nao e sigiloso entram no catalogo. Nos demais (Estados
 * Unidos, Reino Unido, Japao, Alemanha...), a conta segue sem documento: o SSN americano,
 * por exemplo, e sensivel demais para um app de financas pedir so para isso.</p>
 *
 * <p>O front tem o mesmo catalogo em {@code core/ui/documentos.ts}; mudou aqui, muda la.</p>
 */
public final class DocumentosDeIdentidade {

    public static final String BRASIL = "BR";

    private static final Set<String> PAISES = Set.of(Locale.getISOCountries());

    private DocumentosDeIdentidade() {
    }

    /** Documento de um pais do catalogo: o codigo vai para o banco, o nome para a tela. */
    public enum Tipo {
        AR_DNI("AR", "DNI", Pattern.compile("\\d{7,8}"), n -> true),
        BO_CI("BO", "Cédula de Identidad", Pattern.compile("\\d{5,9}"), n -> true),
        CL_RUT("CL", "RUN/RUT", Pattern.compile("\\d{7,8}[0-9K]"), DocumentosDeIdentidade::rutValido),
        CO_CC("CO", "Cédula de Ciudadanía", Pattern.compile("\\d{6,10}"), n -> true),
        EC_CI("EC", "Cédula de Identidad", Pattern.compile("\\d{10}"), DocumentosDeIdentidade::cedulaEquatorianaValida),
        ES_DNI("ES", "DNI/NIE", Pattern.compile("(\\d{8}|[XYZ]\\d{7})[A-Z]"), DocumentosDeIdentidade::dniValido),
        IT_CF("IT", "Codice Fiscale",
                Pattern.compile("[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]"),
                DocumentosDeIdentidade::codiceFiscaleValido),
        MX_CURP("MX", "CURP",
                Pattern.compile("[A-Z][AEIOUX][A-Z]{2}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])[HMX]"
                        + "(AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)"
                        + "[B-DF-HJ-NP-TV-Z]{3}[0-9A-Z]\\d"),
                DocumentosDeIdentidade::curpValida),
        PE_DNI("PE", "DNI", Pattern.compile("\\d{8}"), n -> true),
        PT_NIF("PT", "NIF", Pattern.compile("[1235689]\\d{8}"), DocumentosDeIdentidade::nifValido),
        PY_CI("PY", "Cédula de Identidad", Pattern.compile("\\d{5,8}"), n -> true),
        UY_CI("UY", "Cédula de Identidad", Pattern.compile("\\d{7,8}"), DocumentosDeIdentidade::cedulaUruguaiaValida),
        VE_CI("VE", "Cédula de Identidad", Pattern.compile("\\d{6,9}"), n -> true);

        private final String pais;
        private final String nome;
        private final Pattern formato;
        private final Predicate<String> conta;

        Tipo(String pais, String nome, Pattern formato, Predicate<String> conta) {
            this.pais = pais;
            this.nome = nome;
            this.formato = formato;
            this.conta = conta;
        }

        public String pais() {
            return pais;
        }

        public String nome() {
            return nome;
        }

        /**
         * Tira pontos, tracos, barras e espacos e poe em maiusculas; recusa o que nao
         * tem o formato ou nao fecha o digito verificador.
         *
         * @return o numero como fica guardado
         */
        public String normalizar(String digitado) {
            if (digitado == null || digitado.isBlank()) {
                throw new BusinessException("Informe o número do seu documento (" + nome + ").", HttpStatus.UNPROCESSABLE_CONTENT,
                        "DOCUMENTO_NECESSARIO");
            }
            String limpo = digitado.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);
            if (this == VE_CI) {
                // Venezuela escreve V-12345678 ou E-...; o numero e o que conta.
                limpo = limpo.replaceFirst("^[VE]", "");
            }
            if (!formato.matcher(limpo).matches() || !conta.test(limpo)) {
                throw new BusinessException(nome + " inválido. Confira o número.", HttpStatus.UNPROCESSABLE_CONTENT,
                        "DOCUMENTO_INVALIDO");
            }
            return limpo;
        }
    }

    private static final Map<String, Tipo> POR_PAIS = Arrays.stream(Tipo.values())
            .collect(Collectors.toUnmodifiableMap(Tipo::pais, tipo -> tipo));

    /** Codigo ISO 3166 de duas letras, em maiusculas; recusa o que nao e pais. */
    public static String pais(String digitado) {
        String codigo = digitado == null ? "" : digitado.trim().toUpperCase(Locale.ROOT);
        if (!PAISES.contains(codigo)) {
            throw new BusinessException("Escolha o país onde você mora.", HttpStatus.UNPROCESSABLE_CONTENT,
                    "PAIS_INVALIDO");
        }
        return codigo;
    }

    /** O documento pedido em um pais fora do Brasil; vazio quando la nao se pede nenhum. */
    public static Optional<Tipo> pedidoEm(String pais) {
        return Optional.ofNullable(pais).map(POR_PAIS::get);
    }

    public static Optional<Tipo> tipo(String codigo) {
        return Arrays.stream(Tipo.values()).filter(tipo -> tipo.name().equals(codigo)).findFirst();
    }

    /** Portugal: pesos 9 a 2 nos oito primeiros; resto 0 ou 1 da digito 0. */
    static boolean nifValido(String n) {
        int soma = 0;
        for (int i = 0; i < 8; i++) {
            soma += (n.charAt(i) - '0') * (9 - i);
        }
        int dv = 11 - soma % 11;
        return (dv >= 10 ? 0 : dv) == n.charAt(8) - '0';
    }

    /** Chile: pesos 2 a 7 da direita para a esquerda, modulo 11 (10 vira K). */
    static boolean rutValido(String n) {
        String corpo = n.substring(0, n.length() - 1);
        int soma = 0;
        int peso = 2;
        for (int i = corpo.length() - 1; i >= 0; i--) {
            soma += (corpo.charAt(i) - '0') * peso;
            peso = peso == 7 ? 2 : peso + 1;
        }
        int resto = 11 - soma % 11;
        char dv = resto == 11 ? '0' : resto == 10 ? 'K' : (char) ('0' + resto);
        return dv == n.charAt(n.length() - 1);
    }

    /** Uruguai: sete digitos (com zero a esquerda) com pesos 2987634 e o verificador no fim. */
    static boolean cedulaUruguaiaValida(String n) {
        String cheio = n.length() == 7 ? "0" + n : n;
        int[] pesos = {2, 9, 8, 7, 6, 3, 4};
        int soma = 0;
        for (int i = 0; i < 7; i++) {
            soma += (cheio.charAt(i) - '0') * pesos[i];
        }
        return (10 - soma % 10) % 10 == cheio.charAt(7) - '0';
    }

    /** Equador: provincia 01 a 24 (ou 30), terceiro digito menor que 6 e modulo 10. */
    static boolean cedulaEquatorianaValida(String n) {
        int provincia = Integer.parseInt(n.substring(0, 2));
        if ((provincia < 1 || provincia > 24) && provincia != 30 || n.charAt(2) - '0' >= 6) {
            return false;
        }
        int soma = 0;
        for (int i = 0; i < 9; i++) {
            int produto = (n.charAt(i) - '0') * (i % 2 == 0 ? 2 : 1);
            soma += produto > 9 ? produto - 9 : produto;
        }
        return (10 - soma % 10) % 10 == n.charAt(9) - '0';
    }

    /** Espanha: a letra e o numero modulo 23; no NIE, X, Y e Z valem 0, 1 e 2. */
    static boolean dniValido(String n) {
        String numero = n.substring(0, n.length() - 1)
                .replace('X', '0').replace('Y', '1').replace('Z', '2');
        return "TRWAGMYFPDXBNJZSQVHLCKE".charAt(Integer.parseInt(numero) % 23) == n.charAt(n.length() - 1);
    }

    /** Mexico: valor de cada caractere vezes (18 - posicao), modulo 10. */
    static boolean curpValida(String n) {
        String dicionario = "0123456789ABCDEFGHIJKLMNÑOPQRSTUVWXYZ";
        int soma = 0;
        for (int i = 0; i < 17; i++) {
            soma += dicionario.indexOf(n.charAt(i)) * (18 - i);
        }
        return (10 - soma % 10) % 10 == n.charAt(17) - '0';
    }

    /** Italia: tabelas de posicao impar e par, modulo 26 da a letra final. */
    static boolean codiceFiscaleValido(String n) {
        int[] impares = {1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 2, 4, 18, 20, 11, 3, 6, 8, 12, 14, 16, 10, 22, 25, 24, 23};
        int soma = 0;
        for (int i = 0; i < 15; i++) {
            char c = n.charAt(i);
            int valor = Character.isDigit(c) ? c - '0' : c - 'A';
            soma += i % 2 == 0 ? impares[valor] : valor;
        }
        return (char) ('A' + soma % 26) == n.charAt(15);
    }
}
