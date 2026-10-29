package br.com.piggu.core.ui.formatos

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.format.TextStyle

// A API trabalha com dia civil, sem fuso (datas.ts): por isso LocalDate e YearMonth, e o texto
// lido direto. O dia ISO (AAAA-MM-DD) é o próprio LocalDate.toString().

/** Chave de mês AAAA-MM, que a API usa em metas e no feed (`mesKey` do site). */
fun chaveDoMes(data: LocalDate): String = YearMonth.from(data).toString()

/** "2026-09" vira "Setembro de 2026"; chave que não é mês volta como veio. */
fun mesPorExtenso(chave: String): String {
    val mes = try {
        YearMonth.parse(chave)
    } catch (_: DateTimeParseException) {
        return chave
    }
    val nome = mes.month.getDisplayName(TextStyle.FULL, PtBr).replaceFirstChar { it.titlecase(PtBr) }
    return "$nome de ${mes.year}"
}

/** "2026-09-10" (ou com hora depois) vira "10/09/2026", só pelo texto, sem deslocar o dia. */
fun dataCurta(iso: String?): String {
    if (iso.isNullOrEmpty()) return ""
    val partes = iso.take(10).split('-')
    return if (partes.getOrNull(2).isNullOrEmpty()) iso else "${partes[2]}/${partes[1]}/${partes[0]}"
}

/** Dias do mês, com os vazios do começo (semana começando no domingo) para alinhar na grade. */
fun gradeDoMes(mes: YearMonth): List<Int?> {
    val vazios = mes.atDay(1).dayOfWeek.value % 7
    return List(vazios) { null } + (1..mes.lengthOfMonth()).toList()
}
