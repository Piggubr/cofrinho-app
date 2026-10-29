package br.com.piggu.feature.plano

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.PigguTheme
import br.com.piggu.core.model.InfoDoPlano
import br.com.piggu.core.model.OrigemDaAssinatura
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A tela só desenhada: o "Em breve" no lugar do assinar e o texto de cada origem, sem o portal da Stripe. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TelaDoPlanoTest {
    @get:Rule val compose = createComposeRule()

    private fun desenhar(info: InfoDoPlano, eventos: EventosDoPlano = EventosDoPlano()) {
        compose.setContent { PigguTheme { TelaDoPlano(EstadoDoPlano(Carga.Pronto(info)), eventos) } }
    }

    private fun rolarAte(texto: String) = compose.onNode(hasScrollAction()).performScrollToNode(hasText(texto))

    @Test
    fun `gratuito mostra o preco do app e o em breve desabilitado nos tres planos pagos`() {
        desenhar(GRATUITO)
        compose.onNodeWithText("Em breve você assina o Premium aqui no app, com a cobrança pelo Google Play.").assertExists()
        rolarAte("R$ 22,89/mês")
        compose.onNodeWithText("R$ 19,90", substring = true).assertDoesNotExist()
        rolarAte("R$ 45,99/mês")
        compose.onAllNodesWithText("Em breve").apply {
            fetchSemanticsNodes().let { assertEquals(3, it.size) }
            for (i in 0 until 3) get(i).assertIsNotEnabled()
        }
        compose.onNodeWithText("Assinar", substring = true).assertDoesNotExist()
    }

    @Test
    fun `assinado pelo site explica que gerencia pelo navegador e oferece o reembolso, sem o portal`() {
        var pedidos = 0
        desenhar(PREMIUM_PELO_SITE.copy(reembolsoAte = "2026-11-01T00:00:00Z"), EventosDoPlano(pedirReembolso = { pedidos++ }))
        compose.onNodeWithText("Você tem o Premium individual").assertExists()
        compose.onNodeWithText("Até 08/11/2026").assertExists()
        compose.onNodeWithText("Assinado pelo site", substring = true).assertExists()
        compose.onNodeWithText("Gerenciar assinatura").assertDoesNotExist()
        compose.onNodeWithText("Em breve").assertDoesNotExist()
        compose.onNodeWithText("Cancelar e pedir reembolso").performClick()
        assertEquals(1, pedidos)
        compose.onNodeWithText("Mudou de ideia? Até 01/11/2026 você cancela e recebe tudo de volta.").assertExists()
    }

    @Test
    fun `assinado pela app store manda cancelar na app store`() {
        desenhar(PREMIUM_PELO_SITE.copy(origem = OrigemDaAssinatura.APP_STORE))
        compose.onNodeWithText("Assinado pelo iPhone. Para cancelar, use as assinaturas da App Store.").assertExists()
        compose.onNodeWithText("Cancelar e pedir reembolso").assertDoesNotExist()
    }

    @Test
    fun `assinado pelo google play manda cancelar no google play`() {
        desenhar(PREMIUM_PELO_SITE.copy(origem = OrigemDaAssinatura.PLAY_STORE, planoFamilia = true))
        compose.onNodeWithText("Você tem o Premium família").assertExists()
        compose.onNodeWithText("Assinado pelo app. Para cancelar, use as assinaturas do Google Play.").assertExists()
    }
}
