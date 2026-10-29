package br.com.piggu.feature.contas

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.database.RespostaGuardada
import br.com.piggu.core.database.RespostasGuardadasDao
import br.com.piggu.core.network.ServicoDeBancos
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.testing.DIA_DAS_CAPTURAS
import br.com.piggu.core.testing.RedeDeTeste
import br.com.piggu.core.testing.RespostasFixas
import br.com.piggu.core.testing.redeDeTeste
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** O Room do guardado offline, na memória. */
class GuardadoNaMemoria : RespostasGuardadasDao {
    val respostas = ConcurrentHashMap<String, RespostaGuardada>()
    override suspend fun ler(chave: String) = respostas[chave]
    override suspend fun guardar(resposta: RespostaGuardada) { respostas[resposta.chave] = resposta }
    override suspend fun apagarTudo() = respostas.clear()
}

/**
 * A rede de teste com a Ana logada e o cache na memória. Os ViewModels ficam na [loja], que o
 * teste limpa no fim: senão uma resposta atrasada da API falsa volta para o Main depois do resetMain.
 */
class Montagem(val rede: RedeDeTeste, val fixas: RespostasFixas, private val loja: ViewModelStore) {
    val guardado = GuardadoNaMemoria()
    val lancamentos = Lancamentos()
    val conectado = MutableStateFlow(true)
    private val cache = CacheDeRespostas(guardado, DIA_DAS_CAPTURAS)

    fun contas(financas: ServicoDeFinancas = rede.financas, bancos: ServicoDeBancos = rede.bancos): ContasECartoesViewModel =
        ViewModelProvider.create(loja, viewModelFactory {
            initializer {
                ContasECartoesViewModel(
                    financas, bancos, cache, ListasParaLancar(cache, financas, rede.identidade), rede.sessao, lancamentos, DIA_DAS_CAPTURAS, conectado,
                )
            }
        })["vm${loja.keys().size}", ContasECartoesViewModel::class]

    /** Os pedidos que começam com "METODO /api/rota". */
    fun pedidos(inicio: String) = fixas.pedidos.filter { it.startsWith(inicio) }
}

suspend fun TestScope.montar(pasta: File, loja: ViewModelStore, fixas: RespostasFixas = RespostasFixas()): Montagem =
    Montagem(redeDeTeste(pasta, fixas), fixas, loja).also { it.rede.entrar() }
