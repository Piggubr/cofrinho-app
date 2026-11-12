package br.com.piggu.core.data

import br.com.piggu.core.model.ContaFixa
import br.com.piggu.core.model.SituacaoDaContaFixa
import br.com.piggu.core.model.TipoDeContaFixa
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** O "Ainda dá para gastar" e as parcelas dele, para a conta à vista no cartão. */
data class ContaDoMes(
    val teto: BigDecimal,
    /** O teto é o limite do mês; falso, são as receitas. */
    val tetoEhOLimite: Boolean,
    val gasto: BigDecimal,
    val contasAPagar: BigDecimal,
    /** Negativo é o que passou. */
    val resta: BigDecimal,
    /** Só no mês de hoje e com saldo: o que resta pelos dias que faltam, hoje incluído. */
    val porDia: BigDecimal?,
)

/**
 * A conta do "Ainda dá para gastar" (calcularContaDoMes de ainda-da-para-gastar.ts, o Painel do
 * site): o limite do mês (ou, sem limite, as receitas) menos o que já foi gasto e as contas fixas
 * de gasto que ainda vão sair. A mesma para o Painel e o widget (BUG-002). Sem teto, nula.
 *
 * @param mes aaaa-mm
 * @param contas as contas fixas do mês, todas (as pagas e as de receita ficam de fora aqui)
 */
fun calcularContaDoMes(
    mes: String,
    limite: BigDecimal?,
    receitas: BigDecimal?,
    gasto: BigDecimal,
    contas: List<ContaFixa>,
    hoje: LocalDate,
): ContaDoMes? {
    val tetoEhOLimite = (limite ?: BigDecimal.ZERO).signum() > 0
    val teto = (if (tetoEhOLimite) limite else receitas)?.takeIf { it.signum() > 0 } ?: return null
    val contasAPagar = contas
        .filter { it.situacao != SituacaoDaContaFixa.PAGA && it.tipo != TipoDeContaFixa.RECEITA }
        .fold(BigDecimal.ZERO) { soma, conta -> soma + conta.valor }
    val resta = teto - gasto - contasAPagar
    val porDia = if (mes == YearMonth.from(hoje).toString() && resta.signum() > 0) {
        resta.divide(BigDecimal(hoje.lengthOfMonth() - hoje.dayOfMonth + 1), 2, RoundingMode.DOWN)
    } else {
        null
    }
    return ContaDoMes(teto, tetoEhOLimite, gasto, contasAPagar, resta, porDia)
}

/** O que a fila de gastos já tirou do bolso no mês (aaaa-mm): entra no total e no "Ainda dá" (BUG-010). */
fun List<GastoAguardando>.totalNoMes(mes: String): BigDecimal =
    filter { it.motivo == null && it.lancamento.data.startsWith(mes) }.fold(BigDecimal.ZERO) { soma, guardado ->
        val l = guardado.lancamento
        val parcelas = l.parcelas?.takeIf { it > 1 }
        soma + l.itens.fold(BigDecimal.ZERO) { s, item ->
            s + (parcelas?.let { item.valor.divide(BigDecimal(it), 2, RoundingMode.DOWN) } ?: item.valor)
        }
    }
