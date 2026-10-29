package br.com.piggu.feature.plano

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

/** A tela só desenhada: assinar pelo Google Play só no individual, e o texto de cada origem, sem o portal da Stripe. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TelaDoPlanoTest {
    @get:Rule val compose = createComposeRule()

    private fun desenhar(info: InfoDoPlano, eventos: EventosDoPlano = EventosDoPlano(), precosDaLoja: Map<String, String> = emptyMap()) {
        compose.setContent { PigguTheme { TelaDoPlano(EstadoDoPlano(Carga.Pronto(info), precosDaLoja = precosDaLoja), eventos) } }
    }

    private fun rolarAte(texto: String) = compose.onNode(hasScrollAction()).performScrollToNode(hasText(texto))

    @Test
    fun `gratuito assina o individual pelo google play, com o preco do app sem a loja`() {
        val assinados = mutableListOf<String>()
        var restaurar = 0
        desenhar(GRATUITO, EventosDoPlano(assinar = { assinados += it }, restaurar = { restaurar++ }))
        compose.onNodeWithText("Restaurar compras").performClick()
        rolarAte("R$ 22,89/mês")
        compose.onNodeWithText("R$ 19,90", substring = true).assertDoesNotExist()
        rolarAte("Assinar por mês")
        compose.onNodeWithText("Assinar por mês").performClick()
        rolarAte("Assinar por ano")
        compose.onNodeWithText("Assinar por ano").performClick()
        assertEquals(listOf(PREMIUM_MENSAL, PREMIUM_ANUAL), assinados)
        assertEquals(1, restaurar)
        // Casal e família não se vendem pelo app: um botão de cada, só no individual.
        rolarAte("R$ 45,99/mês")
        assertEquals(1, compose.onAllNodesWithText("Assinar por mês").fetchSemanticsNodes().size)
        rolarAte("Pelo app você assina o Premium individual. O casal e o família não são vendidos pelo app.")
        compose.onNodeWithText("Em breve").assertDoesNotExist()
    }

    @Test
    fun `com o google play mostra o preco da loja`() {
        desenhar(GRATUITO, precosDaLoja = mapOf(PREMIUM_MENSAL to "R$ 24,90", PREMIUM_ANUAL to "R$ 249,00"))
        rolarAte("R$ 24,90/mês")
        compose.onNodeWithText("ou R$ 249,00/ano (2 meses grátis)").assertExists()
        compose.onNodeWithText("R$ 22,89/mês").assertDoesNotExist()
    }

    @Test
    fun `assinado pelo site explica que gerencia pelo navegador e oferece o reembolso, sem o portal`() {
        var pedidos = 0
        desenhar(PREMIUM_PELO_SITE.copy(reembolsoAte = "2026-11-01T00:00:00Z"), EventosDoPlano(pedirReembolso = { pedidos++ }))
        compose.onNodeWithText("Você tem o Premium individual").assertExists()
        compose.onNodeWithText("Até 08/11/2026").assertExists()
        compose.onNodeWithText("Assinado pelo site", substring = true).assertExists()
        compose.onNodeWithText("Gerenciar assinatura").assertDoesNotExist()
        compose.onNodeWithText("Assinar", substring = true).assertDoesNotExist()
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

    @Test
    fun `quem assinou pelo google play gerencia la, sem botao de compra`() {
        var gerenciar = 0
        desenhar(PREMIUM_PELO_SITE.copy(origem = OrigemDaAssinatura.PLAY_STORE), EventosDoPlano(gerenciar = { gerenciar++ }))
        compose.onNodeWithText("Gerenciar assinatura").performClick()
        assertEquals(1, gerenciar)
        compose.onNodeWithText("Assinar", substring = true).assertDoesNotExist()
        assertEquals(
            "https://play.google.com/store/account/subscriptions?sku=premium_anual&package=br.com.piggu.app",
            enderecoDaAssinatura(PREMIUM_ANUAL, "br.com.piggu.app"),
        )
        assertEquals("https://play.google.com/store/account/subscriptions?package=br.com.piggu.app", enderecoDaAssinatura(null, "br.com.piggu.app"))
    }
}
