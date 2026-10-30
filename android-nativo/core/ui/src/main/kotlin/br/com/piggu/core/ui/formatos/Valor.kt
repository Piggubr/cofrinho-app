package br.com.piggu.core.ui.formatos

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Lê um valor digitado do jeito que a pessoa escreveu: 12,50, 12.50, 1.234,56, 1,234.56 ou
 * R$ 12,50. Nulo quando não é um valor. Sempre com duas casas (12,5 vira 12.50).
 *
 * As mesmas regras do site (campo-de-valor.ts) e do iOS (Formatos.lerValor), D-076. Com os dois
 * separadores, o último é o decimal. Vírgula sozinha é decimal e não se repete. Ponto sozinho é
 * milhar quando todo grupo depois dele tem três dígitos e o primeiro não é "0" (1.500 é mil e
 * quinhentos, 0.500 não); senão é decimal, uma vez só. Mais de duas casas, sinal ou letra: nulo.
 */
fun lerValor(texto: String?): BigDecimal? {
    val limpo = (texto ?: "").replace("R$", "").filterNot { it.isWhitespace() }
    if (limpo.isEmpty() || !limpo.all { it in '0'..'9' || it == ',' || it == '.' }) return null
    val virgula = limpo.lastIndexOf(',')
    val ponto = limpo.lastIndexOf('.')
    var inteiro = limpo
    var fracao = ""
    if (virgula >= 0 && ponto >= 0) {
        val decimal = maxOf(virgula, ponto)
        inteiro = limpo.take(decimal).replace(if (decimal == virgula) "." else ",", "")
        fracao = limpo.substring(decimal + 1)
    } else if (virgula >= 0) {
        if (limpo.indexOf(',') != virgula) return null
        inteiro = limpo.take(virgula)
        fracao = limpo.substring(virgula + 1)
    } else if (ponto >= 0) {
        val grupos = limpo.split('.')
        if (grupos.drop(1).all { it.length == 3 } && grupos[0] != "" && grupos[0] != "0") {
            inteiro = grupos.joinToString("")
        } else if (grupos.size == 2) {
            inteiro = grupos[0]
            fracao = grupos[1]
        } else {
            return null
        }
    }
    val soDigitos = { s: String -> s.all { it in '0'..'9' } }
    if (fracao.length > 2 || !soDigitos(inteiro) || !soDigitos(fracao) || (inteiro + fracao).isEmpty()) return null
    return BigDecimal("${inteiro.ifEmpty { "0" }}.${fracao.ifEmpty { "0" }}").setScale(2)
}

/** Valor no padrão brasileiro, sem separador de milhar, para editar sem tropeço: 1234,50. */
fun escreverValor(valor: BigDecimal?): String =
    valor?.setScale(2, RoundingMode.HALF_UP)?.toPlainString()?.replace('.', ',') ?: ""
