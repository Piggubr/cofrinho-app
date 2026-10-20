package com.piggu.identity.avisos;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * Os avisos do dia em texto, na moeda da pessoa: um titulo curto (vira o titulo do push
 * e o assunto do e-mail) e uma linha por aviso.
 */
public record Mensagem(String titulo, List<String> linhas) {

    private static final Locale BRASIL = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    public static Mensagem de(List<Aviso> avisos, String moeda) {
        NumberFormat dinheiro = NumberFormat.getCurrencyInstance(BRASIL);
        try {
            dinheiro.setCurrency(Currency.getInstance(moeda));
        } catch (RuntimeException moedaDesconhecida) {
            // Fica no real; a moeda ja foi validada ao salvar, isto e so cautela.
        }
        List<String> linhas = avisos.stream().map(aviso -> linha(aviso, dinheiro)).toList();
        String titulo = avisos.size() == 1 ? "Piggu: 1 aviso de hoje" : "Piggu: " + avisos.size() + " avisos de hoje";
        return new Mensagem(titulo, linhas);
    }

    /** Corpo do push: so a primeira linha cabe bem; o resto vira "e mais N". */
    public String resumo() {
        return linhas.size() == 1 ? linhas.get(0) : linhas.get(0) + " (e mais " + (linhas.size() - 1) + ")";
    }

    public String texto() {
        return String.join("\n", linhas.stream().map(l -> "- " + l).toList())
                + "\n\nAbra o Piggu para ver os detalhes. Para parar de receber, desligue em Perfil > Avisos.";
    }

    private static String linha(Aviso aviso, NumberFormat dinheiro) {
        String valor = aviso.valor() == null ? "" : dinheiro.format(aviso.valor());
        return switch (aviso.tipo()) {
            case "ORCAMENTO_ATENCAO" -> aviso.assunto() + " ja usou " + inteiro(aviso.percentual())
                    + "% do limite (" + valor + " de " + dinheiro.format(aviso.limite()) + ")";
            case "ORCAMENTO_ESTOUROU" -> aviso.assunto() + " passou do limite: " + valor + " de "
                    + dinheiro.format(aviso.limite());
            case "FATURA_VENCENDO" -> "Fatura do " + aviso.assunto() + " vence em " + DIA.format(aviso.data())
                    + ": " + valor;
            case "CONTA_VENCENDO" -> aviso.assunto() + " vence em " + DIA.format(aviso.data()) + ": " + valor;
            case "CONTA_VENCIDA" -> aviso.assunto() + " venceu em " + DIA.format(aviso.data()) + " e nao foi paga: "
                    + valor;
            case "A_RECEBER_ATRASADO" -> aviso.assunto() + " ainda nao te pagou " + valor + " (combinado para "
                    + DIA.format(aviso.data()) + ")";
            default -> aviso.assunto();
        };
    }

    private static String inteiro(BigDecimal valor) {
        return valor == null ? "?" : valor.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
