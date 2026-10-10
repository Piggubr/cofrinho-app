package com.piggu.finance.integration;

import com.piggu.common.web.Texto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforma o texto que o OCR tirou de um cupom em itens com valor.
 *
 * <p>Regras de mao, sem IA. Cobre os dois formatos que o Piggu ve: DANFE NFC-e
 * brasileira ("001 7891000100103 LEITE 400G 1 UN X 15,99 15,99", com descricao as vezes
 * quebrada numa linha e a quantidade na seguinte) e cupom europeu
 * ("LEITE MIMOSA 1L   0,89 A", com a letra do IVA no fim).</p>
 *
 * <p>Quem diz se a leitura presta e a conta: os itens somados tem de bater com o total
 * impresso. Sem total ou com soma diferente, {@link Leitura#fecha()} e falso e quem chamou
 * decide recorrer ao Gemini.</p>
 */
public final class LeitorDeCupom {

    /** Dinheiro com duas casas: 1.234,56 · 1234,56 · 1234.56 · -0,63. */
    private static final Pattern DINHEIRO =
            Pattern.compile("(?<![\\d,.])-?(?:\\d{1,3}(?:\\.\\d{3})+|\\d+)[,.]\\d{2}(?![\\d,.]*\\d)");

    /** Lixo depois do valor: letra do IVA, "*", "%" de taxa, "€", "R$". */
    private static final Pattern RABO = Pattern.compile("(\\s+([A-Z]|\\*|€|EUR|\\d{1,2}%))+\\s*$");

    /** "2 UN X 24,90", "1,235 KG X 6,99", "2 X 1,15", "2x". */
    private static final Pattern QUANTIDADE = Pattern.compile(
            "\\b\\d+(?:[,.]\\d+)?\\s*(?:UN|UND|KG|G|L|LT|ML|PC|PCT|CX)?\\s*[Xx]\\s*(?:\\d+[,.]\\d{2})?",
            Pattern.CASE_INSENSITIVE);

    /** Numero do item no comeco da linha e codigo de barras/produto (6 a 14 digitos). */
    private static final Pattern NUMERO_DO_ITEM = Pattern.compile("^\\d{1,3}\\s+");
    private static final Pattern CODIGO = Pattern.compile("\\b\\d{6,14}\\b");

    private static final Pattern TOTAL = Pattern.compile(
            "^(valor\\s+a\\s+pagar|total\\s+a\\s+pagar|valor\\s+total|total)\\b");
    private static final Pattern A_PAGAR = Pattern.compile("^(valor|total)\\s+a\\s+pagar\\b");

    /** Linhas que tem valor mas nao sao item: pagamento, imposto, identificacao. */
    private static final Pattern NAO_E_ITEM = Pattern.compile(
            "\\b(subtotal|sub-total|troco|dinheiro|cartao|credito|debito|pix|pagamento|pago|"
                    + "tributos?|impostos?|iva|cnpj|cpf|nif|contribuinte|qtde?|quantidade|saldo|"
                    + "multibanco|mb way|visa|mastercard|acrescimo|descontos)\\b");

    private static final Pattern DESCONTO = Pattern.compile("\\b(desc|desconto|promo|poupanca)\\b");

    private static final Pattern CABECALHO = Pattern.compile(
            "\\b(danfe|nfc-?e|documento|nota fiscal|cnpj|ie|endereco|rua|av|fatura|recibo|consumidor|"
                    + "codigo|descricao|cupom|extrato|sat)\\b");

    private static final Pattern DATA = Pattern.compile(
            "\\b(\\d{2})[/.-](\\d{2})[/.-](\\d{4})\\b|\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");

    /** Tolerancia da conta: arredondamento de peso e de desconto rateado. */
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.05");

    public record Item(String nome, BigDecimal valor) {
    }

    /**
     * @param total valor final impresso (a pagar), nulo se nao foi achado
     */
    public record Leitura(String estabelecimento, LocalDate data, List<Item> itens, BigDecimal total) {

        public BigDecimal soma() {
            return itens.stream().map(Item::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** A leitura so e confiavel quando a soma dos itens bate com o total impresso. */
        public boolean fecha() {
            return !itens.isEmpty() && total != null && soma().subtract(total).abs().compareTo(TOLERANCIA) <= 0;
        }
    }

    private LeitorDeCupom() {
    }

    public static Leitura ler(String texto, LocalDate hoje) {
        List<String> linhas = texto.lines().map(String::strip).filter(linha -> !linha.isEmpty()).toList();

        List<Item> itens = new ArrayList<>();
        BigDecimal total = null;
        boolean totalAPagar = false;
        boolean esperandoValorDoTotal = false;
        String descricaoPendente = null;

        for (String original : linhas) {
            String linha = RABO.matcher(original).replaceFirst("");
            String comparavel = Texto.semAcento(linha.toLowerCase(Locale.ROOT));
            BigDecimal valor = ultimoValor(linha);

            if (esperandoValorDoTotal) {
                esperandoValorDoTotal = false;
                if (valor != null) {
                    total = valor;
                    continue;
                }
            }

            Matcher ehTotal = TOTAL.matcher(comparavel);
            if (ehTotal.find()) {
                boolean aPagar = A_PAGAR.matcher(comparavel).find();
                // "Valor a pagar" (ja com desconto) vence "Valor total"; o primeiro total vence os demais.
                if (total == null || (aPagar && !totalAPagar)) {
                    if (valor != null) {
                        total = valor;
                    } else {
                        esperandoValorDoTotal = true;
                    }
                    totalAPagar = aPagar;
                }
                continue;
            }
            if (total != null && !itens.isEmpty()) {
                // Depois do total vem pagamento e imposto: nada ali e item.
                continue;
            }

            if (valor != null && (valor.signum() < 0 || DESCONTO.matcher(comparavel).find())) {
                if (!itens.isEmpty()) {
                    Item ultimo = itens.remove(itens.size() - 1);
                    itens.add(new Item(ultimo.nome(), ultimo.valor().subtract(valor.abs())));
                }
                continue;
            }
            if (NAO_E_ITEM.matcher(comparavel).find()) {
                descricaoPendente = null;
                continue;
            }

            String descricao = descricao(linha);
            if (valor == null) {
                // Descricao quebrada: o valor vem na proxima linha (NFC-e).
                descricaoPendente = temLetras(descricao) && !CABECALHO.matcher(comparavel).find() ? descricao : null;
                continue;
            }
            if (!temLetras(descricao)) {
                // So quantidade e valor: completa a descricao pendente. Sem pendente e a linha
                // "2 X 1,15" do cupom europeu, que vem antes do item com o valor final.
                if (descricaoPendente != null) {
                    itens.add(new Item(descricaoPendente, valor));
                }
                descricaoPendente = null;
                continue;
            }
            itens.add(new Item(descricao, valor));
            descricaoPendente = null;
        }

        return new Leitura(estabelecimento(linhas), data(texto, hoje), itens, total);
    }

    private static BigDecimal ultimoValor(String linha) {
        Matcher dinheiro = DINHEIRO.matcher(linha);
        String ultimo = null;
        while (dinheiro.find()) {
            ultimo = dinheiro.group();
        }
        if (ultimo == null) {
            return null;
        }
        // A ultima virgula ou ponto e a casa decimal; o resto e milhar.
        int decimal = Math.max(ultimo.lastIndexOf(','), ultimo.lastIndexOf('.'));
        String inteiro = ultimo.substring(0, decimal).replace(".", "").replace(",", "");
        return new BigDecimal(inteiro + "." + ultimo.substring(decimal + 1));
    }

    /** O que sobra da linha sem numero do item, codigo, quantidade e valores. */
    private static String descricao(String linha) {
        // Quantidade antes do numero do item: em "1 UN X 4,79" o 1 e quantidade, nao item.
        String texto = QUANTIDADE.matcher(linha).replaceAll(" ");
        texto = CODIGO.matcher(texto).replaceAll(" ");
        texto = DINHEIRO.matcher(texto).replaceAll(" ");
        texto = NUMERO_DO_ITEM.matcher(texto.strip()).replaceFirst("");
        texto = texto.replaceAll("\\b(R\\$|UN|KG)\\s*$", " ");
        return Texto.limitar(Texto.espacoUnico(texto), 200);
    }

    private static boolean temLetras(String texto) {
        return texto != null && texto.chars().filter(Character::isLetter).count() >= 2;
    }

    /** Primeira linha com cara de nome: tem letras e nao e cabecalho fiscal nem endereco. */
    private static String estabelecimento(List<String> linhas) {
        return linhas.stream()
                .limit(5)
                .filter(linha -> temLetras(linha) && ultimoValor(linha) == null)
                .filter(linha -> !CABECALHO.matcher(Texto.semAcento(linha.toLowerCase(Locale.ROOT))).find())
                .findFirst()
                .map(linha -> Texto.limitar(linha, 200))
                .orElse("");
    }

    /** Primeira data valida que nao esteja no futuro; senao, hoje. */
    private static LocalDate data(String texto, LocalDate hoje) {
        Matcher achada = DATA.matcher(texto);
        while (achada.find()) {
            try {
                LocalDate data = achada.group(1) != null
                        ? LocalDate.parse(achada.group(1) + "/" + achada.group(2) + "/" + achada.group(3),
                                DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        : LocalDate.parse(achada.group(4) + "-" + achada.group(5) + "-" + achada.group(6));
                if (!data.isAfter(hoje)) {
                    return data;
                }
            } catch (DateTimeParseException invalida) {
                // Numero com cara de data que nao e data (ex.: 45/13/2026): segue procurando.
            }
        }
        return hoje;
    }
}
