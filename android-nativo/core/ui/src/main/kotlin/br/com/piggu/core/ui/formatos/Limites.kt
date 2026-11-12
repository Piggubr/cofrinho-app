package br.com.piggu.core.ui.formatos

import androidx.annotation.StringRes
import br.com.piggu.core.ui.R
import java.math.BigDecimal
import java.time.LocalDate

// Os mesmos limites do backend (D-189), conferidos antes de enviar, com texto próprio (D-196).

/** O menor valor de qualquer campo de dinheiro: zero não é gasto nem receita. */
val VALOR_MINIMO = BigDecimal("0.01")

/** Gasto (lançar, editar, item de recibo), orçamento, meta e limite. */
val TETO_DO_GASTO = BigDecimal("1000000.00")

/** Receita, valor a receber e depósito no cofrinho. */
val TETO_DA_RECEITA = BigDecimal("10000000.00")

/** O texto do problema do valor, ou nulo quando ele cabe entre [VALOR_MINIMO] e o [teto]. */
@StringRes
fun problemaDoValor(valor: BigDecimal?, teto: BigDecimal = TETO_DO_GASTO): Int? = when {
    valor == null || valor < VALOR_MINIMO -> R.string.valor_minimo
    valor > teto -> if (teto > TETO_DO_GASTO) R.string.valor_maximo_receita else R.string.valor_maximo_gasto
    else -> null
}

/**
 * Data futura, ou de mais de um ano atrás: pode ser engano de digitação, então o app pergunta
 * antes de lançar ("Essa data é ... Lançar mesmo assim?").
 */
fun dataPedeConfirmacao(data: LocalDate, hoje: LocalDate): Boolean = data > hoje || data < hoje.minusYears(1)
