package br.com.piggu.core.ui.formatos

/**
 * Conferência rápida de e-mail antes de mandar (email.ts): um "@" só, que não é o primeiro
 * caractere, nada de espaço, e um ponto no meio do domínio. Quem decide de verdade é o backend.
 * Sem regex, como no site: ela voltaria atrás demais num texto comprido cheio de pontos.
 */
fun pareceEmail(texto: String): Boolean {
    val arroba = texto.indexOf('@')
    if (arroba < 1 || arroba != texto.lastIndexOf('@') || texto.any { it.isWhitespace() }) return false
    // Ponto com pelo menos um caractere antes e um depois, dentro do domínio.
    val inicio = arroba + 2
    val fim = texto.length - 1
    return inicio < fim && texto.substring(inicio, fim).contains('.')
}
