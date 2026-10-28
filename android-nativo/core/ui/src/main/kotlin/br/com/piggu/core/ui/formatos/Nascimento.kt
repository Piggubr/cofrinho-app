package br.com.piggu.core.ui.formatos

import androidx.annotation.StringRes
import br.com.piggu.core.ui.R
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Idade mínima para ter conta (LGPD art. 14); o backend confere de novo. */
const val IDADE_MINIMA = 13

/** O que há de errado com a data de nascimento, com a mensagem do site (nascimento.ts). */
enum class ProblemaDoNascimento(@StringRes val mensagem: Int) {
    Vazia(R.string.nascimento_vazio),
    Invalida(R.string.nascimento_invalido),
    // O 13 está escrito no texto; mudou IDADE_MINIMA, mude o strings.xml.
    MenorDeIdade(R.string.nascimento_menor_de_idade),
}

/** O problema da data digitada (aaaa-mm-dd), ou nulo quando ela está boa. */
fun problemaDoNascimento(data: String, hoje: LocalDate = LocalDate.now()): ProblemaDoNascimento? {
    if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(data)) return ProblemaDoNascimento.Vazia
    val nascimento = try {
        LocalDate.parse(data)
    } catch (_: DateTimeParseException) {
        return ProblemaDoNascimento.Invalida
    }
    return when {
        nascimento > hoje || hoje.year - nascimento.year > 120 -> ProblemaDoNascimento.Invalida
        nascimento > hoje.minusYears(IDADE_MINIMA.toLong()) -> ProblemaDoNascimento.MenorDeIdade
        else -> null
    }
}
