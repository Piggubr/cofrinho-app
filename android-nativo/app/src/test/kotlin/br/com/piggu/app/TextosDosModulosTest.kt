package br.com.piggu.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * No app, os strings.xml de todos os módulos viram um só: dois módulos com o mesmo nome de texto
 * e conteúdos diferentes fazem um sobrescrever o outro em silêncio. Regressão de 28/10/2026: o
 * título de Cartões e bancos saía "Contas do mês".
 */
class TextosDosModulosTest {
    @Test
    fun `nenhum nome de texto repete com conteudo diferente entre os modulos`() {
        val raiz = File("..").canonicalFile
        val textos = raiz.walkTopDown()
            .onEnter { it.name != "build" && !it.name.startsWith(".") }
            // strings_lugares.xml e strings_filmes.xml: um módulo com várias telas separa os textos por tela.
            .filter { it.name.startsWith("strings") && it.extension == "xml" && it.path.contains("src/main/res/values/") }
            .flatMap { arquivo ->
                Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(arquivo.readText()).map { Triple(it.groupValues[1], it.groupValues[2], arquivo.relativeTo(raiz).path) }
            }
            .toList()
        val conflitos = textos.groupBy { it.first }
            .filterValues { mesmos -> mesmos.map { it.second }.distinct().size > 1 }
            .map { (nome, mesmos) -> "$nome: " + mesmos.joinToString { "${it.third} = \"${it.second}\"" } }
        assertTrue(conflitos.joinToString("\n"), conflitos.isEmpty())
    }
}
