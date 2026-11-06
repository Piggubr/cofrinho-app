package com.piggu.finance.integration;

import com.piggu.common.web.Texto;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforma o texto de uma fatura de cartao (uma linha da fatura por linha de texto) em
 * lancamentos, por regras, sem IA.
 *
 * <p>Quem diz se a leitura presta e a conta: compras mais tarifas, menos estornos, tem de
 * bater com o total impresso. Se nao bater, ou nao houver total, {@link Leitura#fecha()} e
 * falso e quem chamou decide recorrer ao Gemini.</p>
 */
public final class LeitorDeFatura {

    public enum TipoDaLinha { COMPRA, TARIFA, IGNORADA }

    /**
     * @param valor sempre positivo; credito, estorno e pagamento ficam {@link TipoDaLinha#IGNORADA}
     * @param descricao sem o sufixo de parcela "NN/MM"
     */
    public record Linha(LocalDate data, String descricao, BigDecimal valor, TipoDaLinha tipo,
                        Integer parcelaNumero, Integer parcelaTotal, String idExterno) {
    }

    public record Leitura(String banco, LocalDate vencimento, BigDecimal total, List<Linha> linhas) {

        /** Compras e tarifas menos os estornos/creditos; pagamento e saldo anterior nao entram. */
        public BigDecimal soma() {
            BigDecimal soma = BigDecimal.ZERO;
            for (Linha l : linhas) {
                if (l.tipo() != TipoDaLinha.IGNORADA) {
                    soma = soma.add(l.valor());
                } else if (ehCredito(l)) {
                    soma = soma.subtract(l.valor());
                }
            }
            return soma;
        }

        /** Na duvida nao fecha: sem linhas, total ou vencimento, ou com soma fora da tolerancia. */
        public boolean fecha() {
            return !linhas.isEmpty() && total != null && vencimento != null
                    && soma().subtract(total).abs().compareTo(TOLERANCIA) <= 0;
        }
    }

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.05");

    private static final Pattern DATA_NO_COMECO = Pattern.compile("^(\\d{2})/(\\d{2})(?!/)\\s++(.+)$");
    private static final Pattern VALOR = Pattern.compile("^-?(?:\\d{1,3}(?:\\.\\d{3})++|\\d++),\\d{2}-?$");
    private static final Pattern PARCELA = Pattern.compile("^(.*\\S)\\s++(\\d{1,2})/(\\d{1,2})$");
    private static final Pattern VENCIMENTO = Pattern.compile("vencimento[^\\d\\n]{0,20}(\\d{2})/(\\d{2})/(\\d{4})");
    private static final Pattern TOTAL = Pattern.compile("^(?:total\\s+(?:desta|da)\\s+fatura|valor\\s+total)\\b.*");

    private static final Pattern TARIFA = palavras("iof", "anuidade", "juros", "multa", "encargo", "tarifa", "seguro");
    private static final Pattern IGNORADA = palavras("pagamento", "pagto", "credito", "estorno", "saldo anterior", "devolucao");
    private static final Pattern NAO_E_CREDITO = palavras("pagamento", "pagto", "saldo anterior");

    private static final List<String> BANCOS = List.of(
            "nubank", "itau", "inter", "bradesco", "c6", "santander", "caixa", "banco do brasil");

    private LeitorDeFatura() {
    }

    public static Leitura ler(String texto) {
        if (texto == null || texto.isBlank()) {
            return new Leitura("desconhecido", null, null, List.of());
        }
        List<String> linhasDoTexto = texto.lines().map(l -> Texto.espacoUnico(l.replace("R$", " "))).toList();
        LocalDate vencimento = vencimento(Texto.semAcento(texto).toLowerCase());
        BigDecimal total = null;
        List<Linha> linhas = new ArrayList<>();
        for (String l : linhasDoTexto) {
            if (total == null && TOTAL.matcher(Texto.semAcento(l).toLowerCase()).matches()) {
                total = valorNoFim(l);
            }
            Linha linha = vencimento == null ? null : linha(l, vencimento);
            if (linha != null) {
                linhas.add(linha);
            }
        }
        return new Leitura(banco(Texto.semAcento(texto).toLowerCase()), vencimento, total, comIdsGerados(linhas));
    }

    /** Preenche o idExterno das linhas sem ele; a mesma compra repetida ganha ocorrencia 1, 2, ... */
    static List<Linha> comIdsGerados(List<Linha> linhas) {
        Map<String, Integer> vezes = new HashMap<>();
        List<Linha> saida = new ArrayList<>();
        for (Linha l : linhas) {
            if (l.idExterno() != null) {
                saida.add(l);
                continue;
            }
            String base = base(l.data(), l.descricao(), l.valor(), l.parcelaNumero(), l.parcelaTotal());
            int ocorrencia = vezes.merge(base, 1, Integer::sum);
            saida.add(new Linha(l.data(), l.descricao(), l.valor(), l.tipo(), l.parcelaNumero(), l.parcelaTotal(),
                    idExterno(l.data(), l.descricao(), l.valor(), l.parcelaNumero(), l.parcelaTotal(), ocorrencia)));
        }
        return saida;
    }

    /** "fatura:" + sha256(data|descricao|valor|n/m|ocorrencia) truncado a 32; n e m podem ser nulos. */
    static String idExterno(LocalDate data, String descricao, BigDecimal valor, Integer n, Integer m, int ocorrencia) {
        return "fatura:" + sha256(base(data, descricao, valor, n, m) + "|" + ocorrencia).substring(0, 32);
    }

    private static String base(LocalDate data, String descricao, BigDecimal valor, Integer n, Integer m) {
        return data + "|" + descricao.toLowerCase() + "|" + valor.stripTrailingZeros().toPlainString()
                + "|" + (n == null ? "" : n + "/" + m);
    }

    private static Linha linha(String texto, LocalDate vencimento) {
        Matcher d = DATA_NO_COMECO.matcher(texto);
        if (!d.matches()) {
            return null;
        }
        String resto = d.group(3);
        BigDecimal bruto = valorNoFim(resto);
        if (bruto == null) {
            return null;
        }
        String descricao = resto.substring(0, resto.lastIndexOf(' ')).trim();
        Integer n = null;
        Integer m = null;
        Matcher p = PARCELA.matcher(descricao);
        if (p.matches() && Integer.parseInt(p.group(3)) > 1 && Integer.parseInt(p.group(2)) >= 1
                && Integer.parseInt(p.group(2)) <= Integer.parseInt(p.group(3))) {
            descricao = p.group(1);
            n = Integer.parseInt(p.group(2));
            m = Integer.parseInt(p.group(3));
        }
        int mes = Integer.parseInt(d.group(2));
        LocalDate data;
        try {
            // compra de mes depois do vencimento so pode ser do ano anterior (dezembro em fatura de janeiro)
            data = LocalDate.of(mes > vencimento.getMonthValue() ? vencimento.getYear() - 1 : vencimento.getYear(),
                    mes, Integer.parseInt(d.group(1)));
        } catch (DateTimeException invalida) {
            return null;
        }
        String chave = Texto.semAcento(descricao).toLowerCase();
        TipoDaLinha tipo = bruto.signum() < 0 || IGNORADA.matcher(chave).find() ? TipoDaLinha.IGNORADA
                : TARIFA.matcher(chave).find() ? TipoDaLinha.TARIFA : TipoDaLinha.COMPRA;
        return new Linha(data, Texto.limitar(descricao, 200), bruto.abs(), tipo, n, m, null);
    }

    /** Estorno ou credito (nao pagamento nem saldo anterior): abate da soma. */
    private static boolean ehCredito(Linha l) {
        return !NAO_E_CREDITO.matcher(Texto.semAcento(l.descricao()).toLowerCase()).find();
    }

    /** Ultimo token da linha como dinheiro ("1.234,56", "-40,00"), ou nulo. */
    private static BigDecimal valorNoFim(String linha) {
        String token = linha.substring(linha.lastIndexOf(' ') + 1);
        if (!VALOR.matcher(token).matches()) {
            return null;
        }
        boolean negativo = token.startsWith("-") || token.endsWith("-");
        BigDecimal v = new BigDecimal(token.replace("-", "").replace(".", "").replace(',', '.'));
        return negativo ? v.negate() : v;
    }

    private static LocalDate vencimento(String texto) {
        Matcher m = VENCIMENTO.matcher(texto);
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        } catch (DateTimeException invalida) {
            return null;
        }
    }

    /** Primeiro banco conhecido que aparece no texto (o cabecalho). */
    private static String banco(String texto) {
        String achado = "desconhecido";
        int melhor = Integer.MAX_VALUE;
        for (String b : BANCOS) {
            Matcher m = Pattern.compile("\\b" + b + "\\b").matcher(texto);
            if (m.find() && m.start() < melhor) {
                melhor = m.start();
                achado = b;
            }
        }
        return achado;
    }

    private static Pattern palavras(String... termos) {
        return Pattern.compile("\\b(?:" + String.join("|", termos) + ")\\b");
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossivel) {
            throw new IllegalStateException(impossivel);
        }
    }
}
