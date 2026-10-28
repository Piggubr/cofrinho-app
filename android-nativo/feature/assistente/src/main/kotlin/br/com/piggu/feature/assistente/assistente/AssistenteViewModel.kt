package br.com.piggu.feature.assistente.assistente

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.VERSAO_DOS_TERMOS
import br.com.piggu.core.data.ehPremium
import br.com.piggu.core.model.FalaDoAssistente
import br.com.piggu.core.model.PedidoAoAssistente
import br.com.piggu.core.model.PedidoDeAutorizacao
import br.com.piggu.core.model.SituacaoDoAssistente
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.FalhaAoCarregar
import br.com.piggu.feature.assistente.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Falas que vão como contexto para a IA. Igual a AssistenteService.FALAS_NO_CONTEXTO. */
internal const val FALAS_NO_CONTEXTO = 10

/** O maxlength do campo do site e o maxLength da pergunta no contrato. */
const val PERGUNTA_MAXIMA = 500

/** Telas que o assistente pode sugerir abrir (TELAS de assistente.ts); outra rota não vira botão. */
private val TELAS = mapOf(
    "painel" to R.string.assistente_tela_painel,
    "gastos" to R.string.assistente_tela_gastos,
    "receitas" to R.string.assistente_tela_receitas,
    "relatorios" to R.string.assistente_tela_relatorios,
    "orcamentos" to R.string.assistente_tela_orcamentos,
    "contas-fixas" to R.string.assistente_tela_contas_fixas,
    "contas-e-cartoes" to R.string.assistente_tela_contas_e_cartoes,
    "calendario" to R.string.assistente_tela_calendario,
    "planejar" to R.string.assistente_tela_planejar,
    "plano" to R.string.assistente_tela_plano,
)

/** O nome da tela que a resposta sugere, ou nulo quando ela não sugere uma conhecida. */
@StringRes
fun FalaGuardada.nomeDaTela(): Int? = tela?.let(TELAS::get)

data class EstadoDoAssistente(
    val premium: Boolean = false,
    val carregando: Boolean = false,
    /** A situação não carregou; [erro] traz o texto quando é [FalhaAoCarregar.Outra]. */
    val falhaAoCarregar: FalhaAoCarregar? = null,
    val situacao: SituacaoDoAssistente? = null,
    /** A pessoa acabou de autorizar: a próxima pergunta leva a autorização. */
    val autorizandoAgora: Boolean = false,
    val falas: List<FalaGuardada> = emptyList(),
    val texto: String = "",
    val pensando: Boolean = false,
    val erro: MensagemDeErro? = null,
) {
    val desligado: Boolean get() = situacao?.habilitado == false
    val precisaAutorizar: Boolean get() = situacao?.autorizado == false && !autorizandoAgora
    val podeEnviar: Boolean get() = !pensando && texto.isNotBlank()
}

/**
 * Assistente de IA (assistente.ts, Premium): perguntas sobre o dinheiro da família, só leitura.
 * A conversa fica só no aparelho ([ConversaDoAssistente]). No primeiro uso a pessoa autoriza o
 * envio ao Gemini; se gravar a autorização falhar, ela segue junto com a primeira pergunta. Na
 * demonstração, a ApiDaDemo responde com um texto de exemplo.
 */
@HiltViewModel
class AssistenteViewModel @Inject constructor(
    private val sessao: Sessao,
    private val financas: ServicoDeFinancas,
    private val conversa: ConversaDoAssistente,
) : ViewModel() {
    private val estadoAtual = MutableStateFlow(EstadoDoAssistente())
    val estado: StateFlow<EstadoDoAssistente> = estadoAtual.asStateFlow()

    /** De quem são as falas na tela: trocou a pessoa, lê a conversa dela do aparelho. */
    private var dono: String? = null

    // Como o podarConversa do site ao abrir o app: o prazo vale mesmo para quem não abre o assistente.
    private val poda = viewModelScope.launch { conversa.podar() }

    fun abrir() {
        val usuario = sessao.usuario
        val premium = usuario?.ehPremium == true
        estadoAtual.update { it.copy(premium = premium, erro = null) }
        viewModelScope.launch {
            if (usuario?.id != dono) {
                poda.join()
                dono = usuario?.id
                val falas = usuario?.let { conversa.ler(it.id) }.orEmpty()
                // A situação (autorização, limite) é de outra pessoa: busca de novo.
                estadoAtual.update { it.copy(falas = falas, situacao = null, autorizandoAgora = false) }
            }
            if (premium && estadoAtual.value.situacao == null && !estadoAtual.value.carregando) carregar()
        }
    }

    private suspend fun carregar() {
        estadoAtual.update { it.copy(carregando = true, falhaAoCarregar = null) }
        try {
            val situacao = financas.situacaoDoAssistente()
            estadoAtual.update { it.copy(carregando = false, situacao = situacao) }
        } catch (falha: ErroDaApi) {
            estadoAtual.update {
                it.copy(carregando = false, falhaAoCarregar = falhaDe(falha), erro = mensagemDeErro(falha))
            }
        }
    }

    fun tentarDeNovo() {
        viewModelScope.launch { carregar() }
    }

    fun mudarTexto(texto: String) = estadoAtual.update { it.copy(texto = texto.take(PERGUNTA_MAXIMA)) }

    /** "Nova conversa": apaga da tela e do aparelho. */
    fun recomecar() {
        estadoAtual.update { it.copy(falas = emptyList(), erro = null) }
        viewModelScope.launch { conversa.esquecer() }
    }

    fun autorizar() {
        estadoAtual.update { it.copy(autorizandoAgora = true) }
        viewModelScope.launch {
            try {
                val situacao = financas.autorizarAssistente(PedidoDeAutorizacao(VERSAO_DOS_TERMOS))
                estadoAtual.update { it.copy(situacao = situacao, autorizandoAgora = false) }
            } catch (_: ErroDaApi) {
                // Sem gravar agora, a autorização vai junto com a primeira pergunta.
            }
        }
    }

    fun perguntar(texto: String) {
        val pergunta = texto.trim().take(PERGUNTA_MAXIMA)
        val atual = estadoAtual.value
        if (pergunta.isEmpty() || atual.pensando) return
        val historico = atual.falas.takeLast(FALAS_NO_CONTEXTO).map { FalaDoAssistente(it.pessoa, it.texto) }
        val autorizo = atual.autorizandoAgora
        estadoAtual.update {
            it.copy(
                falas = it.falas + FalaGuardada(pessoa = true, texto = pergunta, em = conversa.agora()),
                texto = "",
                erro = null,
                pensando = true,
            )
        }
        viewModelScope.launch {
            guardar()
            try {
                val resposta = financas.perguntarAoAssistente(
                    PedidoAoAssistente(
                        pergunta = pergunta,
                        historico = historico,
                        moeda = sessao.usuario?.preferencias?.moeda ?: MOEDA_SEM_PREFERENCIA,
                        autorizoIa = true.takeIf { autorizo },
                        versaoDoAviso = VERSAO_DOS_TERMOS.takeIf { autorizo },
                    ),
                )
                estadoAtual.update {
                    it.copy(
                        falas = it.falas + FalaGuardada(false, resposta.texto, resposta.tela, conversa.agora()),
                        situacao = it.situacao?.copy(autorizado = true, restantes = resposta.restantes),
                        autorizandoAgora = false,
                        pensando = false,
                    )
                }
            } catch (falha: ErroDaApi) {
                // A pergunta volta para o campo, para não ter de digitar de novo.
                val pedeConsentimento = falha is ErroDaApi.DoServidor && falha.codigo == "CONSENTIMENTO_NECESSARIO"
                estadoAtual.update {
                    it.copy(
                        falas = it.falas.dropLast(1),
                        texto = pergunta,
                        pensando = false,
                        autorizandoAgora = if (pedeConsentimento) false else it.autorizandoAgora,
                        situacao = if (pedeConsentimento) it.situacao?.copy(autorizado = false) else it.situacao,
                        erro = if (pedeConsentimento) null else mensagemDeErro(falha),
                    )
                }
            }
            guardar()
        }
    }

    /** Guarda a conversa da tela no aparelho, em nome de quem conversa. */
    private suspend fun guardar() {
        dono?.let { conversa.guardar(it, estadoAtual.value.falas) }
    }

    private companion object {
        /** MoedaService.codigo do site: sem preferência, o euro. */
        const val MOEDA_SEM_PREFERENCIA = "EUR"

        fun falhaDe(falha: ErroDaApi) = when {
            falha is ErroDaApi.SemInternet -> FalhaAoCarregar.SemInternet
            falha is ErroDaApi.DoServidor && falha.status >= 500 -> FalhaAoCarregar.DoServidor
            else -> FalhaAoCarregar.Outra
        }
    }
}
