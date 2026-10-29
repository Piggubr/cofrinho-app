package br.com.piggu.feature.legal

import androidx.annotation.StringRes

/** Os dois textos de features/legal do site. [arquivo] é o que scripts/textos-legais.py grava em assets. */
enum class DocumentoLegal(val arquivo: String, val caminho: String, @param:StringRes val titulo: Int) {
    Termos("Termos.md", "/termos", R.string.legal_termos),
    Privacidade("Privacidade.md", "/privacidade", R.string.legal_privacidade),
}

/** Um pedaço de texto corrido. [link] que começa com "/" é uma rota do site (link interno). */
data class Trecho(val texto: String, val negrito: Boolean = false, val link: String? = null) {
    val linkInterno: Boolean get() = link?.startsWith("/") == true
}

sealed interface Bloco {
    data class Paragrafo(val trechos: List<Trecho>) : Bloco
    data class Item(val trechos: List<Trecho>) : Bloco

    /** Uma linha das tabelas do Aviso, com o [cabecalho] da tabela para nomear as colunas. */
    data class LinhaDaTabela(val colunas: List<List<Trecho>>, val cabecalho: List<String>) : Bloco
}

/** Um `##` do texto e o que vem até o próximo. */
data class Secao(val titulo: String, val blocos: List<Bloco>)

/** O documento dividido: o `#`, o que vem antes da primeira seção (versão e introdução) e as seções. */
data class TextoLegal(val titulo: String, val abertura: List<Bloco>, val secoes: List<Secao>)

/**
 * Lê o Markdown curto de scripts/textos-legais.py (o mesmo do iOS): blocos separados por linha
 * em branco, `#`, `##`, `- `, `|#` (cabeçalho de tabela), `|` e parágrafos, com `**negrito**` e
 * `[texto](destino)` dentro deles.
 */
fun lerTextoLegal(markdown: String): TextoLegal {
    var titulo = ""
    val abertura = mutableListOf<Bloco>()
    val secoes = mutableListOf<Secao>()
    var cabecalho = emptyList<String>()
    fun acrescentar(bloco: Bloco) {
        val ultima = secoes.lastOrNull()
        if (ultima == null) abertura += bloco else secoes[secoes.lastIndex] = ultima.copy(blocos = ultima.blocos + bloco)
    }
    for (pedaco in markdown.split(Regex("""\n\s*\n"""))) {
        val linha = pedaco.trim()
        when {
            linha.isEmpty() || linha.startsWith("<!--") -> Unit
            linha.startsWith("|#") -> cabecalho = colunas(linha.drop(2)).map { col -> col.joinToString("") { it.texto } }
            linha.startsWith("|") -> acrescentar(Bloco.LinhaDaTabela(colunas(linha.drop(1)), cabecalho))
            linha.startsWith("## ") -> secoes += Secao(linha.drop(3), emptyList())
            linha.startsWith("# ") -> titulo = linha.drop(2)
            linha.startsWith("- ") -> acrescentar(Bloco.Item(trechos(linha.drop(2))))
            else -> acrescentar(Bloco.Paragrafo(trechos(linha)))
        }
    }
    return TextoLegal(titulo, abertura, secoes)
}

private fun colunas(linha: String) = linha.split(" | ").map { trechos(it.trim()) }

private val MARCAS = Regex("""\*\*(.+?)\*\*|\[([^\]]+)]\(([^)\s]+)\)""")

/** `**negrito**` e `[texto](destino)`; `piggu://privacidade` vira a rota `/privacidade`. */
internal fun trechos(texto: String): List<Trecho> = buildList {
    var inicio = 0
    for (marca in MARCAS.findAll(texto)) {
        if (marca.range.first > inicio) add(Trecho(texto.substring(inicio, marca.range.first)))
        val (negrito, rotulo, destino) = marca.destructured
        add(if (negrito.isNotEmpty()) Trecho(negrito, negrito = true) else Trecho(rotulo, link = rota(destino)))
        inicio = marca.range.last + 1
    }
    if (inicio < texto.length) add(Trecho(texto.substring(inicio)))
}

private fun rota(destino: String) = if (destino.startsWith("piggu://")) "/" + destino.removePrefix("piggu://") else destino
