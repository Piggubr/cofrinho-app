package br.com.piggu.feature.calendario

import br.com.piggu.core.model.Gasto
import br.com.piggu.core.model.Nota
import br.com.piggu.core.model.Receita
import br.com.piggu.core.ui.formatos.gradeDoMes
import java.math.BigDecimal
import java.time.YearMonth
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

// As contas do calendário, iguais às do site (calendar.ts e grafico-do-mes.ts) e do iOS (enum Calendario).

/** Um dia da grade, com o que acontece nele. */
data class DiaDaGrade(
    val dia: Int,
    /** aaaa-mm-dd */
    val iso: String,
    val gastos: BigDecimal,
    val receitas: BigDecimal,
    val lembretes: Int,
    val hoje: Boolean,
)

/** Um dia do mês: o que entrou e saiu nele e o acumulado até ele. */
data class DiaDaSerie(
    val dia: Int,
    val receitas: BigDecimal,
    val gastos: BigDecimal,
    val receitasAteAqui: BigDecimal,
    val gastosAteAqui: BigDecimal,
) {
    val sobra: BigDecimal get() = receitasAteAqui - gastosAteAqui
}

/** Uma linha da tabela por semana do mês (1 a 7, 8 a 14...). */
data class SemanaDoMes(val de: Int, val ate: Int, val receitas: BigDecimal, val gastos: BigDecimal) {
    val sobra: BigDecimal get() = receitas - gastos
}

/** Os dias do mês (aaaa-mm) de domingo a sábado, com nulo nos espaços antes do dia 1. */
fun gradeDoCalendario(mes: String, gastos: List<Gasto>, receitas: List<Receita>, notas: List<Nota>, hoje: String): List<DiaDaGrade?> {
    val gastoPorDia = somarPorDia(mes, gastos.map { it.data to it.valor })
    val receitaPorDia = somarPorDia(mes, receitas.map { it.data to it.valor })
    val lembretesPorDia = notas.mapNotNull { it.data }.groupingBy { it }.eachCount()
    return gradeDoMes(YearMonth.parse(mes)).map { dia ->
        dia?.let {
            val iso = "$mes-%02d".format(it)
            DiaDaGrade(it, iso, gastoPorDia[it] ?: BigDecimal.ZERO, receitaPorDia[it] ?: BigDecimal.ZERO, lembretesPorDia[iso] ?: 0, iso == hoje)
        }
    }
}

/**
 * Receitas e gastos do mês, dia a dia e acumulados. No mês corrente a série para em hoje (o resto
 * do mês ainda não aconteceu); num mês futuro fica vazia. Valores de outro mês são ignorados.
 */
fun serieDoMes(mes: String, gastos: List<Gasto>, receitas: List<Receita>, hoje: String): List<DiaDaSerie> {
    val mesDeHoje = hoje.take(7)
    val ultimo = when {
        mes < mesDeHoje -> YearMonth.parse(mes).lengthOfMonth()
        mes == mesDeHoje -> hoje.substring(8, 10).toInt()
        else -> 0
    }
    val gastoPorDia = somarPorDia(mes, gastos.map { it.data to it.valor })
    val receitaPorDia = somarPorDia(mes, receitas.map { it.data to it.valor })
    var receitasAteAqui = BigDecimal.ZERO
    var gastosAteAqui = BigDecimal.ZERO
    return (1..ultimo).map { dia ->
        val receitasNoDia = receitaPorDia[dia] ?: BigDecimal.ZERO
        val gastosNoDia = gastoPorDia[dia] ?: BigDecimal.ZERO
        receitasAteAqui += receitasNoDia
        gastosAteAqui += gastosNoDia
        DiaDaSerie(dia, receitasNoDia, gastosNoDia, receitasAteAqui, gastosAteAqui)
    }
}

/** Uma linha por semana do mês, mesmo as que ainda não aconteceram (ficam zeradas). */
fun semanasDoMes(mes: String, serie: List<DiaDaSerie>): List<SemanaDoMes> {
    val total = YearMonth.parse(mes).lengthOfMonth()
    return (1..total step 7).map { de ->
        val ate = minOf(de + 6, total)
        val dias = serie.filter { it.dia in de..ate }
        SemanaDoMes(de, ate, dias.fold(BigDecimal.ZERO) { soma, d -> soma + d.receitas }, dias.fold(BigDecimal.ZERO) { soma, d -> soma + d.gastos })
    }
}

/** Topo redondo da escala (1; 1,2; 1,5; 2... vezes uma potência de 10) logo acima do maior valor. */
fun topoDaEscala(maior: Double): Double {
    if (maior <= 0) return 1.0
    val potencia = 10.0.pow(floor(log10(maior)))
    val passo = listOf(1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0).first { it * potencia >= maior }
    return passo * potencia
}

/**
 * O dia mais perto do dedo, entre os que já aconteceram ([total], o tamanho da série).
 * [fracao] é a posição na área de desenho, de 0 (dia 1) a 1 (último dia do mês).
 */
fun diaNoPonto(fracao: Float, diasNoMes: Int, total: Int): Int =
    ((fracao * (diasNoMes - 1)).roundToInt() + 1).coerceIn(1, maxOf(total, 1))

private fun somarPorDia(mes: String, lancamentos: List<Pair<String, BigDecimal>>): Map<Int, BigDecimal> =
    lancamentos.filter { (data, _) -> data.startsWith("$mes-") }
        .groupBy({ (data, _) -> data.substring(8, 10).toInt() }, { (_, valor) -> valor })
        .mapValues { (_, valores) -> valores.fold(BigDecimal.ZERO, BigDecimal::add) }
