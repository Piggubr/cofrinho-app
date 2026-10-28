package br.com.piggu.core.designsystem

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

// Os testes rodam na pasta do módulo; o site fica na raiz do repositório.
private fun doSite(caminho: String) = File("../../../frontend/src/app/core/ui/$caminho").readText()

class TemaTest {

    @Test
    fun `as 13 cores tem o id, a ordem e o Premium do tema ts`() {
        val site = Regex("""id: '([a-z]+)',[^}]*?premium: (true|false)""")
            .findAll(doSite("tema.ts"))
            .map { it.groupValues[1] to it.groupValues[2].toBoolean() }
            .toList()
        assertEquals(13, site.size)
        assertEquals(site, VarianteDeCor.entries.map { it.id to it.premium })
    }

    @Test
    fun `id nulo ou desconhecido vira o Chiclete`() {
        assertEquals(VarianteDeCor.Azul, VarianteDeCor.deId("azul"))
        assertEquals(VarianteDeCor.Eletrico, VarianteDeCor.deId("eletrico"))
        assertEquals(VarianteDeCor.Chiclete, VarianteDeCor.deId("verde-limao"))
        assertEquals(VarianteDeCor.Chiclete, VarianteDeCor.deId(null))
    }

    @Test
    fun `sem Premium so Chiclete, Rosa, Menta e Azul ficam`() {
        val gratis = VarianteDeCor.entries.filter { it.efetiva(premium = false) == it }
        assertEquals(
            listOf(VarianteDeCor.Chiclete, VarianteDeCor.Rosa, VarianteDeCor.Menta, VarianteDeCor.Azul),
            gratis,
        )
    }

    @Test
    fun `quando o Premium acaba a cor do Premium volta para o Chiclete`() {
        assertEquals(VarianteDeCor.Chiclete, VarianteDeCor.Grafite.efetiva(premium = false))
        assertEquals(VarianteDeCor.Grafite, VarianteDeCor.Grafite.efetiva(premium = true))
    }

    @Test
    fun `cor gratis fica sem Premium e antes do login nada muda`() {
        assertEquals(VarianteDeCor.Azul, VarianteDeCor.Azul.efetiva(premium = false))
        assertEquals(VarianteDeCor.Vinho, VarianteDeCor.Vinho.efetiva(premium = null))
    }

    @Test
    fun `modo desconhecido segue o aparelho`() {
        assertEquals(ModoDeCor.Escuro, ModoDeCor.deId("escuro"))
        assertEquals(ModoDeCor.Claro, ModoDeCor.deId("claro"))
        assertEquals(ModoDeCor.Automatico, ModoDeCor.deId("sepia"))
        assertEquals(ModoDeCor.Automatico, ModoDeCor.deId(null))
    }

    @Test
    fun `um icone para cada chave do icone ts`() {
        val icones = doSite("icone.ts").substringAfter("const ICONES = {").substringBefore("} satisfies")
        val chaves = Regex("""^ {2}(\w+): \[""", RegexOption.MULTILINE).findAll(icones).map { it.groupValues[1] }.toList()
        assertEquals(chaves, Icone.entries.map { it.chave })
    }
}
