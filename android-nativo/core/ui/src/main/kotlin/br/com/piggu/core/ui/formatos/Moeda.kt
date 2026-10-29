package br.com.piggu.core.ui.formatos

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

internal val PtBr = Locale("pt", "BR")

/** Sem sessão, o euro, a moeda histórica do app, como o MoedaService do site (moeda.ts). */
private const val MOEDA_SEM_SESSAO = "EUR"

/**
 * Valor na moeda da pessoa (`preferencias.moeda`, ISO 4217), com os separadores do Brasil:
 * "R$ 1.234,50", "€ 1,29", "US$ 3,00". Os valores gravados não mudam com a moeda, só o jeito de
 * mostrar. Valor nulo é zero.
 *
 * @param codigo a moeda da pessoa, ou a fixa do valor (saldo de banco em BRL); nulo antes do login.
 */
fun formatarMoeda(valor: BigDecimal?, codigo: String?): String {
    val numero = valor ?: BigDecimal.ZERO
    // O backend só aceita os códigos da JVM; um que este Android não conheça sai com o código na frente.
    val moeda = moedaDe(codigo ?: MOEDA_SEM_SESSAO) ?: return "$codigo " +
        NumberFormat.getNumberInstance(PtBr).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(numero)
    return NumberFormat.getCurrencyInstance(PtBr).apply { currency = moeda }.format(numero)
}

/** Símbolo curto da moeda (€, R$, US$), ou o próprio código quando não há símbolo (CHF). */
fun simboloDaMoeda(codigo: String): String = moedaDe(codigo)?.getSymbol(PtBr) ?: codigo

private fun moedaDe(codigo: String): Currency? = runCatching { Currency.getInstance(codigo) }.getOrNull()

/**
 * "1 BRL = US$ 0,1786": uma unidade da primeira moeda, escrita na segunda (taxaFormatada de
 * shell.ts). Abaixo de 1 mostra até 4 casas, para não virar US$ 0,18.
 */
fun formatarCotacao(de: String, para: String, taxa: BigDecimal): String {
    val moeda = moedaDe(para)
    val formato = if (moeda == null) {
        NumberFormat.getNumberInstance(PtBr)
    } else {
        NumberFormat.getCurrencyInstance(PtBr).apply { currency = moeda }
    }
    formato.minimumFractionDigits = 2
    formato.maximumFractionDigits = if (taxa < BigDecimal.ONE) 4 else 2
    val valor = formato.format(taxa)
    return "1 $de = " + if (moeda == null) "$para $valor" else valor
}
