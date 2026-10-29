package br.com.piggu.core.ui.formatos

// CPF no app: máscara enquanto digita e os dígitos verificadores antes de mandar (cpf.ts).
// Quem decide de verdade é o backend (mesma conta, e CPF único entre contas).

/** Só os dígitos, no máximo 11. */
fun digitosDoCpf(texto: String): String = texto.filter { it in '0'..'9' }.take(11)

/** "52998224725" vira "529.982.247-25", já no meio da digitação. */
fun formatarCpf(texto: String): String {
    val d = digitosDoCpf(texto)
    return when {
        d.length <= 3 -> d
        d.length <= 6 -> "${d.take(3)}.${d.drop(3)}"
        d.length <= 9 -> "${d.take(3)}.${d.substring(3, 6)}.${d.drop(6)}"
        else -> "${d.take(3)}.${d.substring(3, 6)}.${d.substring(6, 9)}-${d.drop(9)}"
    }
}

/** Dígitos verificadores da Receita; números todos iguais não valem. */
fun cpfValido(texto: String): Boolean {
    val d = digitosDoCpf(texto).map { it - '0' }
    if (d.size != 11 || d.all { it == d[0] }) return false
    fun digito(posicao: Int): Int {
        val resto = (0 until posicao).sumOf { d[it] * (posicao + 1 - it) } % 11
        return if (resto < 2) 0 else 11 - resto
    }
    return digito(9) == d[9] && digito(10) == d[10]
}
