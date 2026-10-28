package br.com.piggu.feature.familia

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.ehTitular
import br.com.piggu.core.model.AcaoDeAuditoria
import br.com.piggu.core.model.ConviteDaFamilia
import br.com.piggu.core.model.EventoDeAuditoria
import br.com.piggu.core.model.Familia
import br.com.piggu.core.model.MembroDaFamilia
import br.com.piggu.core.model.PedidoDeEmail
import br.com.piggu.core.model.PedidoDeNome
import br.com.piggu.core.model.PedidoDePapel
import br.com.piggu.core.model.PigguRole
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.network.ServicoDeIdentidade
import br.com.piggu.core.network.historicoDaFamilia
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.formatos.pareceEmail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Autor das mudanças feitas pelo próprio Piggu (conta fixa automática, resgate). */
internal const val SISTEMA = "00000000-0000-0000-0000-000000000000"

/** O que espera o "sim" da pessoa (os confirm do site). */
sealed interface Confirmacao {
    data class AceitarConvite(val convite: ConviteDaFamilia) : Confirmacao
    data object AceitarParceria : Confirmacao
    data class ConvidarParceiro(val membro: MembroDaFamilia) : Confirmacao
    data class VoltarAMembro(val membro: MembroDaFamilia) : Confirmacao
    data class Remover(val membro: MembroDaFamilia) : Confirmacao
    data object Sair : Confirmacao
}

/** Aviso de que deu certo; [nome] entra no texto quando ele pede. */
data class Aviso(@param:StringRes val texto: Int, val nome: String? = null)

/** Quem fez a mudança: alguém da família, o próprio Piggu ou quem já saiu ("Ex-membro"). */
sealed interface Autor {
    data class Pessoa(val nome: String) : Autor
    data object Piggu : Autor
    data object ExMembro : Autor
}

/** Uma linha do Histórico. */
data class LinhaDoHistorico(
    val chave: String,
    val autor: Autor,
    val acao: AcaoDeAuditoria,
    /** "gasto", "conta-fixa"...; "pessoa" não aparece. */
    val entidade: String,
    /** "antes → depois", ou só um deles. */
    val mudanca: String?,
    /** "01/10/2026, 07:00", na hora do aparelho. */
    val quando: String,
)

data class EstadoDaFamilia(
    val familia: Carga<Familia> = Carga.Carregando,
    /** Quando foi guardado o que aparece, se a atualização falhou. */
    val guardadoEm: String? = null,
    val conectado: Boolean = true,
    /** Titular (ou ADMIN): renomeia, convida, remove e muda papel. Os outros veem a lista e saem. */
    val ehTitular: Boolean = false,
    val eu: String? = null,
    /** Convites de outras famílias para o meu e-mail. */
    val convitesParaMim: List<ConviteDaFamilia> = emptyList(),
    val nome: String = "",
    val email: String = "",
    /** Nulo até a pessoa pedir: o histórico só é buscado quando alguém quer ver. */
    val historico: List<LinhaDoHistorico>? = null,
    val carregandoHistorico: Boolean = false,
    val ocupado: Boolean = false,
    val aviso: Aviso? = null,
    val erro: MensagemDeErro? = null,
    val confirmacao: Confirmacao? = null,
) {
    /** O titular me convidou para parceiro e eu ainda não respondi. */
    val conviteDeParceria: Boolean
        get() = (familia as? Carga.Pronto)?.valor?.membros.orEmpty().any { it.id == eu && it.parceriaPendente == true }
}

/**
 * Família (features/family e FamiliaView do iOS): quem faz parte, convites, parceria e o
 * histórico. Aceitar convite ou parceria muda o papel do token: a sessão acaba aqui e a pessoa
 * entra de novo, como no site. Sair da família sai da conta também.
 */
@HiltViewModel
class FamiliaViewModel internal constructor(
    private val identidade: ServicoDeIdentidade,
    private val financas: ServicoDeFinancas,
    private val cache: CacheDeRespostas,
    private val sessao: Sessao,
    private val relogio: Clock,
    conectado: StateFlow<Boolean>,
) : ViewModel() {
    @Inject constructor(
        identidade: ServicoDeIdentidade,
        financas: ServicoDeFinancas,
        cache: CacheDeRespostas,
        sessao: Sessao,
        relogio: Clock,
        rede: MonitorDeRede,
    ) : this(identidade, financas, cache, sessao, relogio, rede.conectado)

    private val recarga = MutableStateFlow(0)
    private val estadoAtual = MutableStateFlow(
        EstadoDaFamilia(ehTitular = sessao.usuario?.ehTitular == true, eu = sessao.usuario?.id),
    )
    val estado: StateFlow<EstadoDaFamilia> = estadoAtual.asStateFlow()

    init {
        viewModelScope.launch {
            combine(recarga, conectado) { _, conectado -> conectado }.collectLatest { conectado ->
                estadoAtual.update { it.copy(conectado = conectado) }
                carregar()
            }
        }
    }

    fun tentarDeNovo() = recarga.update { it + 1 }

    fun mudarNome(nome: String) = estadoAtual.update { it.copy(nome = nome.take(120)) }
    fun mudarEmail(email: String) = estadoAtual.update { it.copy(email = email.take(320)) }

    fun renomear() {
        val nome = estadoAtual.value.nome.trim()
        if (nome.isEmpty()) return falhou(R.string.familia_nome_vazio)
        executar(Aviso(R.string.familia_nome_salvo)) { identidade.renomearFamilia(PedidoDeNome(nome)) }
    }

    fun convidar() {
        val email = estadoAtual.value.email.trim()
        if (!pareceEmail(email)) return falhou(R.string.familia_email_invalido)
        executar(Aviso(R.string.familia_convite_criado), limparEmail = true) { identidade.convidar(PedidoDeEmail(email)) }
    }

    fun cancelarConvite(convite: ConviteDaFamilia) =
        executar(Aviso(R.string.familia_convite_cancelado)) { identidade.cancelarConvite(convite.id) }

    /** Convite de parceria pendente se desfaz sem perguntar; o resto confirma antes, como no site. */
    fun mudarPapel(membro: MembroDaFamilia) {
        if (membro.parceriaPendente == true) {
            executar(Aviso(R.string.familia_parceria_cancelada)) { identidade.mudarPapel(membro.id, PedidoDePapel(PigguRole.MEMBRO)) }
        } else {
            pedir(if (membro.papel == PigguRole.PARCEIRO) Confirmacao.VoltarAMembro(membro) else Confirmacao.ConvidarParceiro(membro))
        }
    }

    fun pedirParaRemover(membro: MembroDaFamilia) = pedir(Confirmacao.Remover(membro))
    fun pedirParaAceitarConvite(convite: ConviteDaFamilia) = pedir(Confirmacao.AceitarConvite(convite))
    fun pedirParaAceitarParceria() = pedir(Confirmacao.AceitarParceria)
    fun pedirParaSair() = pedir(Confirmacao.Sair)
    fun desistir() = estadoAtual.update { it.copy(confirmacao = null) }

    fun recusarParceria() =
        executar(Aviso(R.string.familia_parceria_recusada)) { identidade.recusarParceria() }

    fun confirmar() {
        val confirmacao = estadoAtual.value.confirmacao ?: return
        estadoAtual.update { it.copy(confirmacao = null) }
        when (confirmacao) {
            is Confirmacao.AceitarConvite -> encerrarDepois { identidade.aceitarConvite(confirmacao.convite.id) }
            Confirmacao.AceitarParceria -> encerrarDepois { identidade.aceitarParceria() }
            // O site chama auth.sair(): avisa o servidor e sai deste aparelho.
            Confirmacao.Sair -> encerrarDepois(sair = true) { identidade.sairDaFamilia() }
            is Confirmacao.ConvidarParceiro -> executar(Aviso(R.string.familia_parceria_enviada, confirmacao.membro.nome)) {
                identidade.mudarPapel(confirmacao.membro.id, PedidoDePapel(PigguRole.PARCEIRO))
            }
            is Confirmacao.VoltarAMembro -> executar(Aviso(R.string.familia_papel_alterado)) {
                identidade.mudarPapel(confirmacao.membro.id, PedidoDePapel(PigguRole.MEMBRO))
            }
            is Confirmacao.Remover -> executar(Aviso(R.string.familia_pessoa_removida)) { identidade.removerMembro(confirmacao.membro.id) }
        }
    }

    fun verHistorico() {
        if (estadoAtual.value.carregandoHistorico) return
        estadoAtual.update { it.copy(carregandoHistorico = true) }
        viewModelScope.launch {
            val eventos = historicoDaFamilia(identidade, financas)
            val membros = (estadoAtual.value.familia as? Carga.Pronto)?.valor?.membros.orEmpty()
            estadoAtual.update { e -> e.copy(carregandoHistorico = false, historico = eventos.map { linha(it, membros) }) }
        }
    }

    private fun pedir(confirmacao: Confirmacao) = estadoAtual.update { it.copy(confirmacao = confirmacao) }

    private fun falhou(@StringRes texto: Int) = estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(texto), aviso = null) }

    /** Ações que devolvem a família: mostra a nova e o [aviso]. */
    private fun executar(aviso: Aviso, limparEmail: Boolean = false, acao: suspend () -> Familia) = agir {
        val familia = acao()
        estadoAtual.update {
            it.copy(
                ocupado = false, familia = Carga.Pronto(familia), guardadoEm = null, nome = familia.nome, aviso = aviso,
                email = if (limparEmail) "" else it.email,
            )
        }
    }

    /** O papel no token mudou (ou a pessoa saiu da família): a sessão acaba e o app volta ao login. */
    private fun encerrarDepois(sair: Boolean = false, acao: suspend () -> Unit) = agir {
        acao()
        if (sair) sessao.sair() else sessao.encerrarLocalmente()
    }

    private fun agir(acao: suspend () -> Unit) {
        if (estadoAtual.value.ocupado) return
        estadoAtual.update { it.copy(ocupado = true, erro = null, aviso = null) }
        viewModelScope.launch {
            try {
                acao()
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(ocupado = false, erro = mensagemDeErro(falha)) }
            } finally {
                // Uma atualização só com o resultado; aqui só sobra o caso da sessão encerrada.
                estadoAtual.update { it.copy(ocupado = false) }
            }
        }
    }

    /** A família manda nos estados; os convites para mim, sem rede, ficam como estavam (iOS). */
    private suspend fun carregar() {
        viewModelScope.launch {
            try {
                val convites = identidade.convitesParaMim()
                estadoAtual.update { it.copy(convitesParaMim = convites) }
            } catch (_: ErroDaApi) {
                Unit
            }
        }
        cache.carregar("familia", Familia.serializer()) { identidade.verFamilia() }.collect { carga ->
            estadoAtual.update {
                it.copy(
                    familia = carga,
                    guardadoEm = (carga as? Carga.Pronto)?.guardadaEm?.let(::formatar),
                    nome = (carga as? Carga.Pronto)?.valor?.nome ?: it.nome,
                )
            }
        }
    }

    private fun linha(evento: EventoDeAuditoria, membros: List<MembroDaFamilia>) = LinhaDoHistorico(
        chave = "${evento.entidade}-${evento.id}",
        autor = nomeNaFamilia(membros, evento.autor),
        acao = evento.acao,
        entidade = evento.entidade,
        mudanca = listOfNotNull(evento.antes, evento.depois).joinToString(" → ").ifEmpty { null },
        quando = runCatching { formatar(Instant.parse(evento.quando).toEpochMilli()) }.getOrDefault(evento.quando),
    )

    /** "01/10/2026, 07:00", o toLocaleString('pt-BR') curto do site. */
    private fun formatar(milissegundos: Long): String =
        DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm", Locale.forLanguageTag("pt-BR"))
            .format(Instant.ofEpochMilli(milissegundos).atZone(relogio.zone))
}

/** Quem é o autor, pelo id (nomeNaFamilia de pessoas-da-familia.ts). */
internal fun nomeNaFamilia(membros: List<MembroDaFamilia>, id: String?): Autor = when (id) {
    SISTEMA -> Autor.Piggu
    else -> membros.firstOrNull { it.id == id }?.let { Autor.Pessoa(it.nome) } ?: Autor.ExMembro
}
