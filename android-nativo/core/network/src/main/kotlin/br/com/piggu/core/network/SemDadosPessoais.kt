package br.com.piggu.core.network

private val TOKEN = Regex("""eyJ[\w-]*\.[\w-]+\.[\w-]*""")
private val BUSCA = Regex("""\?[^\s"')]+""")
private val CPF = Regex("""\b\d{3}\.\d{3}\.\d{3}-\d{2}\b""")
private val TELEFONE = Regex("""\(?\b\d{2}\)?\s?\d{4,5}-\d{4}\b""")
private val NUMERO_COMPRIDO = Regex("""\d{8,}""")

/**
 * Tira e-mail, CPF, telefone, token, busca e números compridos de um texto, antes de mandar
 * um erro do aparelho para [ServicoDeIdentidade.registrarErroDoApp]. As mesmas regras do
 * erros-do-app.ts do site; o servidor limpa de novo.
 */
fun semDadosPessoais(texto: String): String =
    semEmails(texto)
        .replace(TOKEN, "[token]")
        .replace(BUSCA, "?[...]")
        .replace(CPF, "[cpf]")
        .replace(TELEFONE, "[telefone]")
        .replace(NUMERO_COMPRIDO, "[numero]")

private fun ehPalavra(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_'

/**
 * Troca cada e-mail por "[email]", andando a partir de cada "@" em vez de uma regex sem
 * âncora, que volta atrás em texto comprido sem e-mail.
 */
private fun semEmails(texto: String): String {
    val saida = StringBuilder()
    var copiado = 0
    var arroba = texto.indexOf('@')
    while (arroba != -1) {
        var inicio = arroba
        while (inicio > copiado && texto[inicio - 1].let { ehPalavra(it) || it in ".%+-" }) inicio--
        var fim = arroba + 1
        while (fim < texto.length && texto[fim].let { ehPalavra(it) || it in ".-" }) fim++
        while (fim > arroba + 1 && texto[fim - 1].let { !ehPalavra(it) && it != '-' }) fim--
        // Domínio: algo antes do primeiro ponto e algo depois dele.
        val ponto = texto.substring(arroba + 1, fim).indexOf('.')
        if (inicio < arroba && ponto > 0 && ponto < fim - arroba - 2) {
            saida.append(texto, copiado, inicio).append("[email]")
            copiado = fim
        }
        arroba = texto.indexOf('@', maxOf(arroba + 1, copiado))
    }
    return saida.append(texto, copiado, texto.length).toString()
}
