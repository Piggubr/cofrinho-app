package br.com.piggu.widget

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.datastore.PreferenciasDoAparelho
import br.com.piggu.core.model.ItemDeGasto
import br.com.piggu.core.model.MetaDoMes
import br.com.piggu.core.model.NovoLancamento
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import br.com.piggu.core.testing.esperar
import br.com.piggu.core.testing.redeDeTeste
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.math.BigDecimal

/** O resumo dos widgets com a sessão e a rede de verdade, respondidas pela API da demonstração (Ana). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AtualizadorDoWidgetTest {
    @get:Rule val pasta = TemporaryFolder()

    @Test
    fun `grava o resumo do mes da Ana, le e apaga ao sair`() = runTest {
        val rede = redeDeTeste(pasta.root)
        val guarda = GuardaDoResumo(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { pasta.root.resolve("w.preferences_pb") }))
        val preferencias = PreferenciasDoAparelho(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { pasta.root.resolve("a.preferences_pb") }))
        val lancamentos = Lancamentos()
        AtualizadorDoWidget(
            ApplicationProvider.getApplicationContext(), rede.sessao, rede.financas, rede.estiloDeVida,
            preferencias, lancamentos, guarda, DIA_DAS_CAPTURAS, backgroundScope,
        ).vigiar(voltas = flowOf(Unit))
        val resumo = guarda.resumo.stateIn(backgroundScope, SharingStarted.Eagerly, null)

        rede.entrar()
        val gravado = resumo.esperar { it != null }!!
        assertEquals("2026-10", gravado.mes)
        assertEquals(0, BigDecimal(4500).compareTo(gravado.limite))
        // A primeira meta ainda aberta da Ana; das três, uma acabou.
        assertEquals(ResumoDoWidget.Meta("Ler 2 livros", 1, 2), gravado.meta)
        assertEquals(1 to 3, gravado.metasConcluidas to gravado.metasTotal)
        assertEquals("chiclete", gravado.cor)

        // Lançou um gasto: o resumo é refeito.
        rede.financas.lancarGastos(NovoLancamento(data = "2026-10-28", itens = listOf(ItemDeGasto("Café", "Alimentação", BigDecimal("12.50")))))
        lancamentos.avisar()
        resumo.esperar { it != null && it.gasto.compareTo(gravado.gasto + BigDecimal("12.50")) == 0 }

        rede.sessao.sair()
        assertNull(resumo.esperar { it == null })
    }

    @Test
    fun `o resumo pega a primeira meta aberta, ou a ultima, e ignora limite zero`() {
        fun meta(titulo: String, concluida: Boolean) = MetaDoMes("id-$titulo", titulo, 2, if (concluida) 2 else 1, "2026-10", concluida)
        val todasAcabaram = resumoDoMes("2026-10", BigDecimal.TEN, BigDecimal.ZERO, listOf(meta("a", true), meta("b", true)), "BRL", null, 0)
        assertEquals("b", todasAcabaram.meta?.titulo)
        assertNull(todasAcabaram.limite)
        assertNull(todasAcabaram.porcentagemDoLimite)

        val estourou = resumoDoMes("2026-10", BigDecimal(150), BigDecimal(100), listOf(meta("a", true), meta("b", false)), "BRL", null, 0)
        assertEquals("b", estourou.meta?.titulo)
        assertEquals(150, estourou.porcentagemDoLimite)
        assertEquals(0, BigDecimal(-50).compareTo(estourou.restante))
    }
}
