package br.com.piggu.core.ui.formatos

import java.time.LocalDate
import java.time.YearMonth

// A fatura do cartão, com as mesmas regras do servidor (fatura.ts e Fatura.swift).

/** Fechamento padrão: 7 dias antes do vencimento, voltando ao mês anterior quando preciso. */
fun fechamentoPadrao(vencimento: Int): Int = if (vencimento > 7) vencimento - 7 else vencimento + 24

/** A fatura em que a compra cai: o mês (AAAA-MM) e o vencimento dela (AAAA-MM-DD). */
data class FaturaDaCompra(val mes: String, val vencimento: String)

/**
 * Em que fatura cai uma compra (AAAA-MM-DD): antes do dia do fechamento, na fatura do mês; do
 * fechamento em diante, na do mês seguinte. Dia 31 vira o último dia do mês curto.
 */
fun faturaDaCompra(fechamento: Int, vencimento: Int, compra: String): FaturaDaCompra {
    val dia = LocalDate.parse(compra)
    val mesDaCompra = YearMonth.from(dia)
    val daFatura = if (dia.dayOfMonth < minOf(fechamento, mesDaCompra.lengthOfMonth())) mesDaCompra else mesDaCompra.plusMonths(1)
    val doVencimento = if (vencimento > fechamento) daFatura else daFatura.plusMonths(1)
    return FaturaDaCompra(daFatura.toString(), doVencimento.atDay(minOf(vencimento, doVencimento.lengthOfMonth())).toString())
}
