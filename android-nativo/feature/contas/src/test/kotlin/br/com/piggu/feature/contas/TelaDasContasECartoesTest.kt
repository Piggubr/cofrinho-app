package br.com.piggu.feature.contas

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.PigguTheme
import br.com.piggu.core.model.ContaBancaria
import br.com.piggu.core.model.ContaOuCartao
import br.com.piggu.core.model.Fatura
import br.com.piggu.core.model.TipoDeConta
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.math.BigDecimal

private val NUBANK = ContaOuCartao(
    "c1", "Nubank", TipoDeConta.CARTAO, 5, 12,
    faturaAberta = Fatura("2026-11", "2026-10-05", "2026-11-04", "2026-11-05", "2026-11-12", BigDecimal("80"), false),
    usuario = "u-ana",
)
private val CONTA_DO_BANCO = ContaBancaria("b1", "conexao-1", "Banco Exemplo", "Conta corrente", "BANK", null, BigDecimal("100"), "BRL", "UPDATED")

/** A tela só desenhada: o "Conectar banco" depende do Open Finance; atualizar e desconectar, do banco conectado. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TelaDasContasECartoesTest {
    @get:Rule val compose = createComposeRule()

    private fun desenhar(estado: EstadoDasContasECartoes, eventos: EventosDasContasECartoes = EventosDasContasECartoes()) {
        compose.setContent { PigguTheme { TelaDasContasECartoes(estado, eventos) } }
    }

    private fun rolarAte(texto: String) = compose.onNode(hasScrollAction()).performScrollToNode(hasText(texto))

    @Test
    fun `open finance desligado e banco conectado mostra atualizar e desconectar, sem conectar`() {
        desenhar(EstadoDasContasECartoes(contas = Carga.Pronto(listOf(NUBANK)), eu = "u-ana", bancos = listOf(CONTA_DO_BANCO)))
        compose.onNodeWithText("Fecha dia 5 · vence dia 12").assertExists()
        rolarAte("Atualizar saldos")
        compose.onNodeWithText("Atualizar saldos").assertExists()
        compose.onNodeWithText("Desconectar").assertExists()
        compose.onNodeWithText("Conectar banco").assertDoesNotExist()
    }

    @Test
    fun `open finance ligado abre o site para conectar o banco`() {
        var aberto = 0
        desenhar(
            EstadoDasContasECartoes(contas = Carga.Pronto(emptyList()), openFinanceLigado = true),
            EventosDasContasECartoes(conectarBanco = { aberto++ }),
        )
        compose.onNodeWithText("Nenhuma conta ainda").assertExists()
        rolarAte("Conectar banco")
        compose.onNodeWithText("Conectar banco").performClick()
        assertEquals(1, aberto)
        compose.onNodeWithText("Atualizar saldos").assertDoesNotExist()
    }

    @Test
    fun `open finance desligado sem banco nao mostra os bancos`() {
        desenhar(EstadoDasContasECartoes(contas = Carga.Pronto(listOf(NUBANK)), eu = "u-ana"))
        rolarAte("Salvar")
        compose.onNodeWithText("Bancos conectados").assertDoesNotExist()
    }
}
