package br.com.piggu.app

import android.content.Context
import androidx.lifecycle.ViewModel
import br.com.piggu.feature.login.esquecerAContaDoGoogle
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.EstadoDaSessao
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.ehPremium
import br.com.piggu.core.data.ehTitular
import br.com.piggu.core.data.modulosLigados
import br.com.piggu.core.data.pendencia
import br.com.piggu.core.data.Pendencia
import br.com.piggu.core.datastore.ConversaGuardada
import br.com.piggu.core.datastore.PreferenciasDoAparelho
import br.com.piggu.core.designsystem.ModoDeCor
import br.com.piggu.core.designsystem.VarianteDeCor
import br.com.piggu.core.model.Cotacao
import br.com.piggu.core.model.Usuario
import br.com.piggu.core.network.AvisoDeVersao
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.feature.perfil.IconesDoApp
import br.com.piggu.core.ui.formatos.formatarCotacao
import br.com.piggu.app.moldura.PerfilDaMoldura
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** O que a raiz do app mostra: login, pendência, a moldura ou o "atualize". */
sealed interface Raiz {
    data object Abrindo : Raiz
    data object Login : Raiz
    data class Pendente(val pendencia: Pendencia) : Raiz
    data class Logado(val perfil: PerfilDaMoldura) : Raiz
    data object Atualizar : Raiz
}

data class EstadoDoApp(
    val raiz: Raiz = Raiz.Abrindo,
    val cor: VarianteDeCor = VarianteDeCor.Chiclete,
    val modo: ModoDeCor = ModoDeCor.Automatico,
    val coresDoCelular: Boolean = false,
    /** O Premium acabou e a cor voltou ao padrão: a moldura avisa uma vez. */
    val avisarCorPadrao: Boolean = false,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val sessao: Sessao,
    private val preferencias: PreferenciasDoAparelho,
    private val financas: ServicoDeFinancas,
    private val conversa: ConversaGuardada,
    private val iconesDoApp: IconesDoApp,
    aviso: AvisoDeVersao,
) : ViewModel() {

    private val cotacao = MutableStateFlow<Cotacao?>(null)

    val estado: StateFlow<EstadoDoApp> = combine(
        sessao.estado,
        aviso.precisaAtualizar,
        combine(preferencias.cor, preferencias.modo, preferencias.coresDoCelular, preferencias.voltouAoPadrao, ::Aparencia),
        cotacao,
    ) { sessao, atualizar, aparencia, cotacao ->
        val usuario = (sessao as? EstadoDaSessao.Logado)?.usuario
        EstadoDoApp(
            raiz = when {
                atualizar -> Raiz.Atualizar
                sessao is EstadoDaSessao.Verificando -> Raiz.Abrindo
                usuario == null -> Raiz.Login
                usuario.pendencia != null -> Raiz.Pendente(usuario.pendencia!!)
                else -> Raiz.Logado(usuario.paraAMoldura(cotacao))
            },
            // Antes do login o plano não se sabe: a cor escolhida vale (tema.ts).
            cor = VarianteDeCor.deId(aparencia.cor).efetiva(usuario?.ehPremium),
            modo = ModoDeCor.deId(aparencia.modo),
            coresDoCelular = aparencia.coresDoCelular,
            avisarCorPadrao = aparencia.voltou,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EstadoDoApp())

    private val pessoaLogada = sessao.estado
        .map { (it as? EstadoDaSessao.Logado)?.usuario?.takeIf { u -> u.pendencia == null }?.id }
        .distinctUntilChanged()

    /** O tour do primeiro acesso (abrirSeNovo do Tutorial do site): uma vez por pessoa neste aparelho. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val tourNovo: StateFlow<Boolean> = pessoaLogada
        .flatMapLatest { id -> if (id == null) flowOf(false) else preferencias.viuOTour(id).map { !it } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Fechou o tour, no fim ou pelo "Pular". */
    fun viuOTour() {
        viewModelScope.launch { pessoaLogada.first()?.let { preferencias.marcarTourVisto(it) } }
    }

    init {
        viewModelScope.launch { sessao.restaurar() }
        // Premium que acabou: a cor Premium volta ao Chiclete e a moldura avisa (marcarQueVoltouAoPadrao).
        viewModelScope.launch {
            sessao.estado.map { (it as? EstadoDaSessao.Logado)?.usuario?.ehPremium }.distinctUntilChanged().collect { premium ->
                if (premium == false && VarianteDeCor.deId(preferencias.cor.first()).premium) {
                    preferencias.voltarAoPadrao(VarianteDeCor.Chiclete.id)
                }
                // O ícone Premium também volta ao padrão (o Clássico, grátis, fica).
                if (premium != null) iconesDoApp.voltarAoPadraoSemPremium(premium)
            }
        }
        // Saiu, apagou a conta ou a sessão caiu: o próximo "Entrar com o Google" pergunta a conta de novo.
        viewModelScope.launch {
            sessao.estado.map { it is EstadoDaSessao.Deslogado }.distinctUntilChanged().collect { deslogado ->
                if (deslogado) {
                    esquecerAContaDoGoogle(contexto)
                    // A conversa com o assistente traz números da família: sai junto com a conta.
                    conversa.esquecerConversa()
                }
            }
        }
        // A cotação do topo, refeita quando a pessoa troca as moedas ou liga a cotação no Perfil.
        viewModelScope.launch {
            sessao.estado.map { (it as? EstadoDaSessao.Logado)?.usuario?.preferencias }.distinctUntilChanged().collect { prefs ->
                val de = prefs?.moedaCotacao ?: prefs?.moeda
                val para = prefs?.moedaConversao
                cotacao.value = if (prefs?.mostrarCotacao != true || de == null || para == null || de == para) {
                    null
                } else {
                    // A cotação é informativa: sem ela o app segue normalmente.
                    runCatching { financas.consultarCotacao(de, para) }.getOrNull()
                }
            }
        }
    }

    fun avisouCorPadrao() {
        viewModelScope.launch { preferencias.esquecerQueVoltouAoPadrao() }
    }

    /** Os gastos guardados sem internet ficam e vão quando a pessoa entrar de novo, como no iOS (D-123). */
    fun sair() {
        viewModelScope.launch { sessao.sair() }
    }

    private data class Aparencia(val cor: String?, val modo: String?, val coresDoCelular: Boolean, val voltou: Boolean)
}

private fun Usuario.paraAMoldura(cotacao: Cotacao?) = PerfilDaMoldura(
    nome = nome,
    email = email,
    ehTitular = ehTitular,
    premium = ehPremium,
    modulos = modulosLigados,
    cotacao = cotacao?.let { formatarCotacao(it.de, it.para, it.taxa) },
    cotacaoDesatualizada = cotacao?.desatualizada == true,
    emailConfirmado = emailConfirmado != false,
    cpfInformado = cpfInformado != false,
)
