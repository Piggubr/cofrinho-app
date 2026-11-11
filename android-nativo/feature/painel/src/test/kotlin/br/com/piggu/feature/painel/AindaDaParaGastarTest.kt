package br.com.piggu.feature.painel

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import br.com.piggu.core.network.MensagemDeErro
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.PigguTheme
import br.com.piggu.core.model.Cofrinho
import br.com.piggu.core.model.ContaFixa
import br.com.piggu.core.model.Gasto
import br.com.piggu.core.model.ResumoDoMes
import br.com.piggu.core.model.SituacaoDaContaFixa
import br.com.piggu.core.model.TipoDeContaFixa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.math.BigDecimal

/** O número fixo no topo do Painel (D-164): teto do mês menos o gasto e as contas fixas que ainda vão sair. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AindaDaParaGastarTest {
    @get:Rule val compose = createComposeRule()

    private fun gasto(valor: String) = Gasto(id = valor, data = "2026-11-02", item = "Mercado", categoria = "Mercado", valor = BigDecimal(valor))

    private fun conta(valor: String, situacao: SituacaoDaContaFixa = SituacaoDaContaFixa.PENDENTE, tipo: TipoDeContaFixa? = TipoDeContaFixa.GASTO) =
        ContaFixa(valor, "Luz", "Casa", BigDecimal(valor), 10, false, "2026-11-10", situacao, tipo)

    private fun resumo(receitas: String) =
        ResumoDoMes("2026-11", BigDecimal(receitas), BigDecimal.ZERO, BigDecimal(receitas), gastosMesAnterior = BigDecimal.ZERO)

    /** 02/11/2026: faltam 29 dias no mês, hoje incluído. */
    private fun estado(
        limite: String? = null,
        gastos: List<Gasto> = emptyList(),
        contas: List<ContaFixa> = emptyList(),
        resumo: ResumoDoMes? = null,
        mes: String = "2026-11",
    ) = EstadoDoPainel(
        mes = mes,
        mesAtual = "2026-11",
        hoje = "2026-11-02",
        moeda = "BRL",
        carga = Carga.Pronto(
            Nucleo(Cofrinho(totalDepositos = BigDecimal.ZERO, totalGastos = BigDecimal.ZERO, saldo = BigDecimal.ZERO), gastos, limite?.let { mapOf(mes to BigDecimal(it)) }.orEmpty()),
        ),
        contasDoMes = contas,
        resumo = resumo,
        resumoRespondeu = true,
    )

    @Test
    fun `com limite, tira o gasto e so as contas de gasto que faltam pagar`() {
        val e = estado(
            limite = "4500",
            gastos = listOf(gasto("3000"), gasto("260")),
            // A receita fixa não é conta a pagar.
            contas = listOf(conta("187"), conta("119", SituacaoDaContaFixa.VENCIDA), conta("6200", tipo = TipoDeContaFixa.RECEITA)),
            resumo = resumo("9000"),
        )
        assertTrue(e.tetoEhOLimite)
        assertEquals(BigDecimal("306"), e.contasAPagar)
        assertEquals(BigDecimal("934"), e.aindaDaParaGastar)
        assertEquals(BigDecimal("32.20"), e.porDia)
        assertTrue(e.mostraAindaDaParaGastar)
    }

    @Test
    fun `sem limite, o teto sao as receitas do mes`() {
        val e = estado(gastos = listOf(gasto("1000")), contas = listOf(conta("500")), resumo = resumo("6200"))
        assertFalse(e.tetoEhOLimite)
        assertEquals(BigDecimal("6200"), e.tetoDoMes)
        assertEquals(BigDecimal("4700"), e.aindaDaParaGastar)
    }

    @Test
    fun `sem limite e sem receita nao ha numero`() {
        assertNull(estado(gastos = listOf(gasto("10"))).aindaDaParaGastar)
        assertNull(estado(resumo = resumo("0")).aindaDaParaGastar)
        assertNull(estado().porDia)
    }

    @Test
    fun `passou do teto fica negativo e sem valor por dia`() {
        val e = estado(limite = "100", gastos = listOf(gasto("150")))
        assertEquals(BigDecimal("-50"), e.aindaDaParaGastar)
        assertNull(e.porDia)
    }

    @Test
    fun `mes passado nao divide por dia, e o membro nao ve o numero`() {
        assertNull(estado(limite = "100", mes = "2026-10").porDia)
        assertFalse(estado(limite = "100").copy(ehMembro = true).mostraAindaDaParaGastar)
        assertFalse(estado(limite = "100").copy(carga = Carga.Carregando).mostraAindaDaParaGastar)
    }

    @Test
    fun `o cartao mostra o valor, o por dia e a conta`() {
        val e = estado(limite = "4500", gastos = listOf(gasto("3260")), contas = listOf(conta("187")))
        compose.setContent { PigguTheme { CartaoAindaDaParaGastar(e, {}) } }
        compose.onNodeWithText("Ainda dá para gastar", substring = true).assertExists()
        compose.onNodeWithText("1.053,00", substring = true).assertExists()
        compose.onNodeWithText("por dia", substring = true).assertExists()
        compose.onNodeWithText("4.500,00", substring = true).assertExists()
        compose.onNodeWithText("Limite do mês menos o que já foi gasto e as contas a pagar").assertExists()
    }

    @Test
    fun `o cartao avisa quando passou e no mes passado diz quanto sobrou`() {
        compose.setContent { PigguTheme { CartaoAindaDaParaGastar(estado(limite = "100", gastos = listOf(gasto("150")), mes = "2026-10"), {}) } }
        compose.onNodeWithText("Sobrou em", substring = true).assertExists()
        compose.onNodeWithText("Já passou do que dava para gastar este mês.").assertExists()
        compose.onNodeWithText("-R$", substring = true).assertExists()
    }

    @Test
    fun `enquanto as contas carregam nao ha numero, e o erro nao vira zero de contas`() {
        val carregando = estado(limite = "4500", gastos = listOf(gasto("1000"))).copy(contasDoMes = null)
        assertTrue(carregando.contaCarregando)
        assertNull(carregando.aindaDaParaGastar)
        val comErro = carregando.copy(erroDasContas = MensagemDeErro.DoApp(R.string.gastar_carregando))
        assertFalse(comErro.contaCarregando)
        assertNull(comErro.aindaDaParaGastar)
        // Sem limite, o resumo (as receitas) também precisa ter chegado.
        assertTrue(estado(contas = emptyList()).copy(resumoRespondeu = false).contaCarregando)
    }

    @Test
    fun `os gastos da fila sem internet entram no gasto e no ainda da`() {
        val e = estado(limite = "4500", gastos = listOf(gasto("1000"))).copy(aguardandoNoMes = BigDecimal("500"))
        assertEquals(BigDecimal("1500"), e.gastoNoMes)
        assertEquals(BigDecimal("3000"), e.aindaDaParaGastar)
    }

    @Test
    fun `o cartao carregando, com erro e o tentar de novo`() {
        var tentou = 0
        val comErro = estado(limite = "4500").copy(contasDoMes = null, erroDasContas = MensagemDeErro.DoServidor("Fora do ar."))
        var atual by mutableStateOf(estado(limite = "4500").copy(contasDoMes = null))
        compose.setContent { PigguTheme { CartaoAindaDaParaGastar(atual, {}, tentarDeNovo = { tentou++ }) } }
        compose.onNodeWithText("Somando as contas do mês…").assertExists()
        compose.onNodeWithText("de contas a pagar", substring = true).assertDoesNotExist()
        atual = comErro
        compose.onNodeWithText("Fora do ar.", substring = true).assertExists()
        compose.onNodeWithText("Tentar de novo").performClick()
        assertEquals(1, tentou)
    }

    @Test
    fun `sem teto, o cartao leva ao limite ou as receitas`() {
        val abertos = mutableListOf<String>()
        compose.setContent { PigguTheme { CartaoAindaDaParaGastar(estado(), { abertos += it }) } }
        compose.onNodeWithText("Definir limite").performClick()
        compose.onNodeWithText("Lançar receitas").performClick()
        // As receitas abrem no mês do Painel.
        assertEquals(listOf("/orcamentos", "/receitas?mes=${estado().mes}"), abertos)
    }
}
