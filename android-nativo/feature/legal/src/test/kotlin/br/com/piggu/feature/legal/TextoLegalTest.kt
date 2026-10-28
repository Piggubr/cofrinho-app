package br.com.piggu.feature.legal

import br.com.piggu.core.data.VERSAO_DOS_TERMOS
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Os testes rodam na pasta do módulo; o site fica na raiz do repositório.
private fun doApp(arquivo: String) = File("src/main/assets/$arquivo").readText()
private fun doSite(caminho: String) = File("../../../frontend/src/app/$caminho").readText()

@OptIn(ExperimentalCoroutinesApi::class)
class TextoLegalTest {

    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun devolver() = Dispatchers.resetMain()

    @Test
    fun `divide em secoes e reconhece listas, tabelas, negrito e links`() {
        val texto = lerTextoLegal(
            """
            <!-- gerado -->

            # Termos

            Versão 1

            ## 1. Primeira

            - Leia o [Aviso](piggu://privacidade) e **isto**.

            Escreva para [nos](mailto:a@b).

            ## 2. Segunda

            |#Dado | Para quê

            |CPF | **Fraude**
            """.trimIndent(),
        )
        assertEquals("Termos", texto.titulo)
        assertEquals(listOf(Bloco.Paragrafo(listOf(Trecho("Versão 1")))), texto.abertura)
        assertEquals(listOf("1. Primeira", "2. Segunda"), texto.secoes.map { it.titulo })

        val item = texto.secoes[0].blocos[0] as Bloco.Item
        assertEquals(
            listOf(Trecho("Leia o "), Trecho("Aviso", link = "/privacidade"), Trecho(" e "), Trecho("isto", negrito = true), Trecho(".")),
            item.trechos,
        )
        assertTrue(item.trechos[1].linkInterno)
        val email = (texto.secoes[0].blocos[1] as Bloco.Paragrafo).trechos[1]
        assertEquals(Trecho("nos", link = "mailto:a@b"), email)
        assertFalse(email.linkInterno)

        assertEquals(
            Bloco.LinhaDaTabela(listOf(listOf(Trecho("CPF")), listOf(Trecho("Fraude", negrito = true))), listOf("Dado", "Para quê")),
            texto.secoes[1].blocos.single(),
        )
    }

    @Test
    fun `os dois documentos tem titulo, versao e secoes com texto`() {
        DocumentoLegal.entries.forEach { documento ->
            val texto = lerTextoLegal(doApp(documento.arquivo))
            assertTrue(documento.name, texto.titulo.isNotBlank())
            assertTrue(documento.name, texto.secoes.size >= 5)
            assertTrue(documento.name, texto.secoes.all { it.blocos.isNotEmpty() && !it.titulo.contains("}") })
        }
        val privacidade = lerTextoLegal(doApp(DocumentoLegal.Privacidade.arquivo))
        assertTrue(privacidade.secoes.flatMap { it.blocos }.any { it is Bloco.LinhaDaTabela })
    }

    @Test
    fun `a versao dos termos do app e a do aviso ts do site, e o texto mostra ela`() {
        val site = Regex("""VERSAO_DO_AVISO = '([^']+)'""").find(doSite("core/privacidade/aviso.ts"))!!.groupValues[1]
        assertEquals(site, VERSAO_DOS_TERMOS)
        DocumentoLegal.entries.forEach { assertTrue(doApp(it.arquivo).contains("Versão $site")) }
    }

    @Test
    fun `a VM le o arquivo do documento e divide em secoes`() {
        val viewModel = TextoLegalViewModel(DocumentoLegal.Termos, ::doApp, Dispatchers.Main)
        val estado = viewModel.estado.value
        assertFalse(estado.carregando)
        assertEquals("Termos de Uso do Piggu", estado.texto?.titulo)
        assertEquals(16, estado.texto?.secoes?.size)
    }

    @Test
    fun `sem o arquivo a VM fica em erro`() {
        val viewModel = TextoLegalViewModel(DocumentoLegal.Privacidade, { throw FileNotFoundException(it) }, Dispatchers.Main)
        assertNull(viewModel.estado.value.texto)
        assertTrue(viewModel.estado.value.falhou)
    }
}
