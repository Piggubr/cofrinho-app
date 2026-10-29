package br.com.piggu.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import br.com.piggu.core.datastore.PreferenciasDoAparelho
import br.com.piggu.core.testing.esperar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TravaViewModelTest {
    @get:Rule val pasta = TemporaryFolder()
    private var agora = 0L

    // Sem resetMain, como nos outros testes de ViewModel.
    @Before fun principal() = Dispatchers.setMain(UnconfinedTestDispatcher())

    private fun TestScope.preferencias() =
        PreferenciasDoAparelho(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { pasta.newFile("a.preferences_pb") }))

    @Test
    fun `com a trava ligada, abre travado e so trava de novo depois de um minuto fora`() = runTest {
        val preferencias = preferencias()
        preferencias.mudarTravar(true)
        val trava = TravaViewModel(preferencias) { agora }
        trava.estado.esperar { it.travado == true }

        trava.desbloqueou()
        trava.estado.esperar { it.travado == false }

        // O PIN, a câmera ou o site tiram o app da frente por pouco tempo: não trava.
        trava.foiParaOFundo()
        agora += TEMPO_FORA_SEM_TRAVAR - 1
        trava.voltou()
        trava.estado.esperar { it.travado == false }

        trava.foiParaOFundo()
        agora += TEMPO_FORA_SEM_TRAVAR
        trava.voltou()
        trava.estado.esperar { it.travado == true }
    }

    @Test
    fun `ligar a trava no Perfil nao trava na hora`() = runTest {
        val preferencias = preferencias()
        val trava = TravaViewModel(preferencias) { agora }
        trava.estado.esperar { it.travado == false }

        preferencias.mudarTravar(true)
        preferencias.mudarEsconderNosRecentes(true)
        trava.estado.esperar { it.esconderNosRecentes && it.travado == false }
    }
}
