package br.com.piggu.feature.painel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.EstadoDaSessao
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.ehPremium
import br.com.piggu.core.data.mesAtual
import br.com.piggu.core.data.modulosLigados
import br.com.piggu.core.datastore.PainelDoAparelho
import br.com.piggu.core.model.AReceber
import br.com.piggu.core.model.AlertaDoOrcamento
import br.com.piggu.core.model.Cofrinho
import br.com.piggu.core.model.ContaBancaria
import br.com.piggu.core.model.ContaFixa
import br.com.piggu.core.model.ContaOuCartao
import br.com.piggu.core.model.Decimal
import br.com.piggu.core.model.Gasto
import br.com.piggu.core.model.ItemDoGirlsMath
import br.com.piggu.core.model.MembroDaFamilia
import br.com.piggu.core.model.MetaDoMes
import br.com.piggu.core.model.Nota
import br.com.piggu.core.model.NovaNota
import br.com.piggu.core.model.NovoDeposito
import br.com.piggu.core.model.ObjetivoDoCofrinho
import br.com.piggu.core.model.Orcamento
import br.com.piggu.core.model.PedidoDeObjetivo
import br.com.piggu.core.model.PedidoDePreferencias
import br.com.piggu.core.model.PigguRole
import br.com.piggu.core.model.ResumoDoMes
import br.com.piggu.core.model.SituacaoDaContaFixa
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeBancos
import br.com.piggu.core.network.ServicoDeEstiloDeVida
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.network.ServicoDeIdentidade
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.Visao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** O que vem junto, como o forkJoin do site, e fica guardado para abrir sem internet ("painel/aaaa-mm[/visao]"). */
@Serializable
data class Nucleo(
    val cofrinho: Cofrinho,
    val gastos: List<Gasto>,
    /** aaaa-mm para o limite daquele mês. */
    val metas: Map<String, Decimal>,
)

/** Para ficar de olho (avisos-do-mes.ts), já filtrado. */
data class AvisosDoMes(
    val orcamentos: List<Orcamento> = emptyList(),
    val faturas: List<ContaOuCartao> = emptyList(),
    val contas: List<ContaFixa> = emptyList(),
    val devendo: List<AReceber> = emptyList(),
) {
    val vazio: Boolean get() = orcamentos.isEmpty() && faturas.isEmpty() && contas.isEmpty() && devendo.isEmpty()
}

data class TotalDaCategoria(val categoria: String, val total: BigDecimal, val fracao: Float)

/** Novo objetivo ([id] nulo) ou editar um. [prazo] aaaa-mm-dd. */
data class FormularioDeObjetivo(
    val id: String? = null,
    val nome: String = "",
    val alvo: BigDecimal? = null,
    val prazo: String? = null,
    val compartilhado: Boolean = false,
)

data class NotaNova(val titulo: String = "", val texto: String = "")

/** Ação em andamento: desliga o botão dela. */
enum class Acao { Deposito, Objetivo, Nota, Moeda, Bancos }

/** As moedas mais comuns de quem usa o Piggu; a lista inteira fica no Perfil. */
private val MOEDAS_DO_GUIA = listOf("BRL", "EUR", "USD", "GBP", "ARS", "CLP", "UYU", "JPY")

/** Autor das mudanças feitas pelo próprio Piggu (pessoas-da-familia.ts). */
private const val SISTEMA = "00000000-0000-0000-0000-000000000000"

/** O membro só vê os próprios depósitos e o resto zerado: estes ficam escondidos (dashboard.ts). */
private val ESCONDIDOS_DO_MEMBRO = setOf(IdDoBloco.Resumo, IdDoBloco.Mes, IdDoBloco.Categorias, IdDoBloco.Objetivos)

/** Quantas notas cabem no Painel; o resto fica no Calendário. */
private const val NOTAS_NO_PAINEL = 5

data class EstadoDoPainel(
    val mes: String,
    val mesAtual: String,
    val hoje: String,
    val primeiroNome: String = "",
    /** A moeda da pessoa (preferencias.moeda); nula antes do login. */
    val moeda: String? = null,
    val eu: String? = null,
    val ehMembro: Boolean = false,
    val ehPremium: Boolean = false,
    val girlsMathLigado: Boolean = true,
    val visao: Visao = Visao.Tudo,
    val membros: List<MembroDaFamilia> = emptyList(),
    val carga: Carga<Nucleo> = Carga.Carregando,
    val conectado: Boolean = true,
    val atualizando: Boolean = false,
    val blocos: List<BlocoDoPainel> = PainelOrganizado.padrao,
    val guia: EstadoDoGuia = EstadoDoGuia(),
    val resumo: ResumoDoMes? = null,
    val avisos: AvisosDoMes = AvisosDoMes(),
    /** Nulo enquanto carrega. */
    val objetivos: List<ObjetivoDoCofrinho>? = null,
    val notas: List<Nota>? = null,
    val metasDoMes: List<MetaDoMes>? = null,
    val girlsMath: List<ItemDoGirlsMath>? = null,
    /** Nulo com o Open Finance desligado (ou o serviço fora): o cartão some. */
    val bancos: List<ContaBancaria>? = null,
    val erroDosBancos: MensagemDeErro? = null,
    /** Erro de carregar ou de uma ação, por cartão (o resumo leva o do guia). */
    val erros: Map<IdDoBloco, MensagemDeErro> = emptyMap(),
    val ocupado: Set<Acao> = emptySet(),
    val deposito: BigDecimal? = null,
    val valoresDosObjetivos: Map<String, BigDecimal?> = emptyMap(),
    val formulario: FormularioDeObjetivo? = null,
    /** O backend recusou o segundo objetivo no gratuito: o formulário dá lugar ao convite. */
    val limiteDoGratuito: Boolean = false,
    val notaNova: NotaNova? = null,
    val moedaEscolhida: String? = null,
) {
    private val nucleo: Nucleo? get() = (carga as? Carga.Pronto)?.valor

    val guardadaEm: Long? get() = (carga as? Carga.Pronto)?.guardadaEm

    /** Mora mais alguém na casa: só assim faz sentido o "compartilhado". */
    val moraComAlguem: Boolean get() = membros.size > 1

    /** Os cartões na ordem escolhida, sem os escondidos e sem o que não vale para a pessoa. */
    val cartoes: List<IdDoBloco>
        get() = blocos.filter { it.visivel }.map { it.bloco }.filter { bloco ->
            !(ehMembro && bloco in ESCONDIDOS_DO_MEMBRO) && (bloco != IdDoBloco.GirlsMath || girlsMathLigado) &&
                // Sem resumo (falhou) ou sem aviso, o cartão some, como no site.
                (bloco != IdDoBloco.Resumo || resumo != null) && (bloco != IdDoBloco.Avisos || !avisos.vazio)
        }

    /** O cartão dos bancos vai junto do "mes", como no site; sem ele, junto do primeiro. */
    val bancosJuntoDe: IdDoBloco? get() = if (bancos == null) null else cartoes.firstOrNull { it == IdDoBloco.Mes } ?: cartoes.firstOrNull()

    val gastoNoMes: BigDecimal get() = nucleo?.gastos?.fold(BigDecimal.ZERO) { soma, gasto -> soma + gasto.valor } ?: BigDecimal.ZERO
    val limiteDoMes: BigDecimal get() = nucleo?.metas?.get(mes) ?: BigDecimal.ZERO
    val progressoDoLimite: Int
        get() = if (limiteDoMes.signum() <= 0) 0 else minOf(100, (gastoNoMes * BigDecimal(100) / limiteDoMes).toDouble().let(Math::round).toInt())
    val estourouOLimite: Boolean get() = limiteDoMes.signum() > 0 && gastoNoMes > limiteDoMes

    /** Total de cada categoria no mês, do maior para o menor, com a fatia do total. */
    val porCategoria: List<TotalDaCategoria>
        get() {
            val total = gastoNoMes
            return nucleo?.gastos.orEmpty().groupBy { it.categoria }
                .map { (categoria, gastos) ->
                    val soma = gastos.fold(BigDecimal.ZERO) { s, g -> s + g.valor }
                    TotalDaCategoria(categoria, soma, if (total.signum() > 0) (soma.toDouble() / total.toDouble()).toFloat() else 0f)
                }
                .sortedByDescending { it.total }
        }

    // Primeiros passos (primeiros-passos.ts): limite e gasto contam pelo que já existe na conta.
    val temLimite: Boolean get() = nucleo?.metas?.values?.any { it.signum() > 0 } == true
    val temGasto: Boolean get() = nucleo?.let { it.gastos.isNotEmpty() || it.cofrinho.totalGastos.signum() > 0 } == true
    val moedaFeita: Boolean get() = guia.moeda == true
    val passosFeitos: Int get() = listOf(moedaFeita, temLimite, temGasto).count { it }
    val mostraGuia: Boolean get() = nucleo != null && !ehMembro && guia.fechado != true && !(temLimite && temGasto)
    val moedasDoGuia: List<String> get() = (listOfNotNull(moeda) + MOEDAS_DO_GUIA).distinct()

    /** Espelha o backend: o gratuito tem um objetivo (o que o parceiro compartilhou não conta). */
    val podeCriarObjetivo: Boolean
        get() = !limiteDoGratuito && (ehPremium || objetivos.orEmpty().count { ehMeu(it.usuario) } < 1)

    /** Só quem criou decide se é a dois. */
    val podeCompartilhar: Boolean
        get() = moraComAlguem && formulario?.id?.let { id -> objetivos.orEmpty().firstOrNull { it.id == id }?.let { ehMeu(it.usuario) } } != false

    val notasRecentes: List<Nota> get() = notas.orEmpty().sortedByDescending { it.criadoEm }.take(NOTAS_NO_PAINEL)
    val metasConcluidas: Int get() = metasDoMes.orEmpty().count { it.concluida }
    val percentualDasMetas: Int
        get() = metasDoMes.orEmpty().let { if (it.isEmpty()) 0 else Math.round(it.count { m -> m.concluida } * 100.0 / it.size).toInt() }

    /** Quanto deixou de gastar no mês de hoje (totaisDoGirlsMath). */
    val girlsMathNoMes: BigDecimal
        get() = girlsMath.orEmpty().filter { it.data.startsWith(mesAtual) }.fold(BigDecimal.ZERO) { s, i -> s + i.valor }

    /** Soma por moeda: somar real com euro daria um número sem sentido. */
    val saldoPorMoeda: List<Pair<String, BigDecimal>>
        get() = bancos.orEmpty().groupBy { it.moeda }.map { (moeda, contas) -> moeda to contas.fold(BigDecimal.ZERO) { s, c -> s + c.saldo } }

    /** Um banco por conexão, para o "Desconectar" não repetir por conta. */
    val conexoes: List<Pair<String, String>> get() = bancos.orEmpty().associate { it.conexaoId to it.instituicao }.toList()

    fun ehMeu(usuario: String?): Boolean = usuario == null || usuario == eu

    /** O nome de quem lançou; nulo é ex-membro. */
    fun nome(usuario: String?): String? = if (usuario == SISTEMA) "Piggu" else membros.firstOrNull { it.id == usuario }?.nome
}

/**
 * O Painel (dashboard.ts e os cartões dele; PainelView e ModeloDoPainel do iOS).
 *
 * O núcleo (cofrinho, gastos do mês e limites) vem junto e fica guardado para abrir sem internet.
 * O resto é informativo: cada parte que falha some ou mostra o erro no próprio cartão, e o Painel
 * segue, como no site.
 */
@HiltViewModel
class PainelViewModel @Inject constructor(
    private val sessao: Sessao,
    private val cache: CacheDeRespostas,
    private val listas: ListasParaLancar,
    private val financas: ServicoDeFinancas,
    private val bancos: ServicoDeBancos,
    private val estiloDeVida: ServicoDeEstiloDeVida,
    private val identidade: ServicoDeIdentidade,
    private val aparelho: PainelDoAparelho,
    lancamentos: Lancamentos,
    rede: MonitorDeRede,
    private val relogio: Clock,
) : ViewModel() {
    private val estadoAtual = MutableStateFlow(
        relogio.mesAtual().let { EstadoDoPainel(mes = it, mesAtual = it, hoje = LocalDate.now(relogio).toString()) },
    )
    val estado: StateFlow<EstadoDoPainel> = estadoAtual.asStateFlow()

    /** A chave do aparelho, como o site: `piggu_painel_<id>`. */
    private val pessoa = sessao.usuario?.id ?: "anonimo"
    private var carregamento: Job? = null

    init {
        viewModelScope.launch {
            sessao.estado.collect { sessao ->
                val usuario = (sessao as? EstadoDaSessao.Logado)?.usuario ?: return@collect
                estadoAtual.update {
                    it.copy(
                        primeiroNome = usuario.primeiroNome,
                        moeda = usuario.preferencias?.moeda,
                        eu = usuario.id,
                        ehMembro = usuario.role == PigguRole.MEMBRO,
                        ehPremium = usuario.ehPremium,
                        girlsMathLigado = usuario.modulosLigados?.contains(IdDoBloco.GirlsMath.id) != false,
                    )
                }
            }
        }
        viewModelScope.launch { aparelho.ordem(pessoa).collect { json -> estadoAtual.update { it.copy(blocos = PainelOrganizado.ordemValida(json)) } } }
        viewModelScope.launch { aparelho.guia(pessoa).collect { json -> estadoAtual.update { it.copy(guia = EstadoDoGuia.deJson(json)) } } }
        // Lançado pelo + ou pela fila com esta tela aberta: recarrega (a primeira leitura é só a inicial).
        viewModelScope.launch { lancamentos.versao.drop(1).collect { carregar() } }
        // A rede voltou: troca o guardado pelo de agora.
        viewModelScope.launch {
            rede.conectado.collect { conectado ->
                val voltou = conectado && !estadoAtual.value.conectado
                estadoAtual.update { it.copy(conectado = conectado) }
                if (voltou) carregar()
            }
        }
        carregar()
    }

    // Carregar

    fun mudarMes(mes: String) {
        estadoAtual.update { it.copy(mes = mes) }
        carregar()
    }

    fun trocarVisao(visao: Visao) {
        estadoAtual.update { it.copy(visao = visao) }
        carregar()
    }

    /** Puxar para atualizar. */
    fun atualizar() {
        estadoAtual.update { it.copy(atualizando = true) }
        carregar()
    }

    fun carregar() {
        carregamento?.cancel()
        val (mes, visao) = estadoAtual.value.let { it.mes to it.visao }
        val parametro = visao.id.takeIf { visao != Visao.Tudo }
        val sufixo = parametro?.let { "/$it" }.orEmpty()
        carregamento = viewModelScope.launch {
            launch {
                cache.carregar("resumo/$mes$sufixo", ResumoDoMes.serializer()) { financas.resumoDoMes(mes, parametro) }
                    .collect { carga -> estadoAtual.update { it.copy(resumo = (carga as? Carga.Pronto)?.valor) } }
            }
            launch { carregarAvisos(mes) }
            launch { listas.familia().collect { carga -> (carga as? Carga.Pronto)?.let { f -> estadoAtual.update { it.copy(membros = f.valor.membros) } } } }
            launch { recarregarObjetivos() }
            launch { buscar(IdDoBloco.Notas) { financas.listarNotas() }.let { notas -> estadoAtual.update { it.copy(notas = notas ?: it.notas.orEmpty()) } } }
            launch { buscar(IdDoBloco.Metas) { estiloDeVida.listarMetasDoMes() }.let { metas -> estadoAtual.update { it.copy(metasDoMes = metas.orEmpty()) } } }
            launch { buscar(IdDoBloco.GirlsMath) { estiloDeVida.listarGirlsMath() }.let { itens -> estadoAtual.update { it.copy(girlsMath = itens.orEmpty()) } } }
            launch { carregarBancos() }
            cache.carregar("painel/$mes$sufixo", Nucleo.serializer()) {
                coroutineScope {
                    val cofrinho = async { financas.consultarCofrinho() }
                    val gastos = async { financas.listarGastos(mes, parametro) }
                    val metas = async { financas.listarMetas() }
                    Nucleo(cofrinho.await(), gastos.await(), metas.await())
                }
            }.collect { carga -> estadoAtual.update { it.copy(carga = carga) } }
            estadoAtual.update { it.copy(atualizando = false) }
        }
    }

    private suspend fun carregarAvisos(mes: String) = coroutineScope {
        val hoje = LocalDate.now(relogio)
        val limiteDaFatura = hoje.plusDays(10).toString()
        val contas = async { buscar(null) { financas.contasFixas(mes) }.orEmpty() }
        val orcamentos = async { buscar(null) { financas.orcamentos(mes) }.orEmpty() }
        // A fatura não depende do mês escolhido: é sempre a próxima a vencer.
        val cartoes = async { (listas.contas().last() as? Carga.Pronto)?.valor.orEmpty() }
        val aReceber = async { buscar(null) { financas.aReceber() }.orEmpty() }
        val avisos = AvisosDoMes(
            orcamentos = orcamentos.await().filter { it.alerta != AlertaDoOrcamento.OK },
            faturas = cartoes.await().filter { c ->
                c.faturaAPagar?.let { it.paga != true && it.total.signum() > 0 && it.vencimento <= limiteDaFatura } == true
            },
            contas = contas.await().filter { it.situacao != SituacaoDaContaFixa.PAGA },
            devendo = aReceber.await().filter { it.data < hoje.toString() },
        )
        estadoAtual.update { it.copy(avisos = avisos) }
    }

    /** Open Finance é opcional: desligado ou fora do ar esconde o cartão, sem erro. */
    private suspend fun carregarBancos() {
        val habilitado = buscar(null) { bancos.status() }?.habilitado == true
        if (!habilitado) {
            estadoAtual.update { it.copy(bancos = null) }
            return
        }
        try {
            val contas = bancos.listarContas()
            estadoAtual.update { it.copy(bancos = contas, erroDosBancos = null) }
        } catch (erro: ErroDaApi) {
            estadoAtual.update { it.copy(bancos = it.bancos.orEmpty(), erroDosBancos = mensagemDeErro(erro)) }
        }
    }

    /** A chamada, ou nulo no erro (marcado no cartão [bloco], quando houver). */
    private suspend fun <T> buscar(bloco: IdDoBloco?, chamada: suspend () -> T): T? = try {
        chamada().also { if (bloco != null) limparErro(bloco) }
    } catch (erro: ErroDaApi) {
        if (bloco != null) mostrarErro(bloco, mensagemDeErro(erro))
        null
    }

    private fun mostrarErro(bloco: IdDoBloco, erro: MensagemDeErro) = estadoAtual.update { it.copy(erros = it.erros + (bloco to erro)) }
    private fun limparErro(bloco: IdDoBloco) = estadoAtual.update { it.copy(erros = it.erros - bloco) }

    /** Roda uma ação marcada como ocupada; o erro vai para o cartão. */
    private fun agir(acao: Acao, bloco: IdDoBloco, executar: suspend () -> Unit) {
        if (acao in estadoAtual.value.ocupado) return
        estadoAtual.update { it.copy(ocupado = it.ocupado + acao, erros = it.erros - bloco) }
        viewModelScope.launch {
            try {
                executar()
            } catch (erro: ErroDaApi) {
                mostrarErro(bloco, mensagemDeErro(erro))
            } finally {
                estadoAtual.update { it.copy(ocupado = it.ocupado - acao) }
            }
        }
    }

    private val hoje: String get() = LocalDate.now(relogio).toString()

    // Cofrinho

    fun mudarDeposito(valor: BigDecimal?) = estadoAtual.update { it.copy(deposito = valor) }

    fun depositar() {
        val valor = estadoAtual.value.deposito
        if (valor == null || valor.signum() <= 0) return mostrarErro(IdDoBloco.Cofrinho, MensagemDeErro.DoApp(R.string.deposito_invalido))
        agir(Acao.Deposito, IdDoBloco.Cofrinho) {
            financas.depositar(NovoDeposito(valor, hoje))
            estadoAtual.update { it.copy(deposito = null) }
            recarregarCofrinho()
        }
    }

    fun excluirDeposito(id: String) = agir(Acao.Deposito, IdDoBloco.Cofrinho) {
        financas.excluirDeposito(id)
        recarregarCofrinho()
    }

    private suspend fun recarregarCofrinho() {
        val cofrinho = buscar(IdDoBloco.Cofrinho) { financas.consultarCofrinho() } ?: return
        estadoAtual.update { estado ->
            val pronto = estado.carga as? Carga.Pronto ?: return@update estado
            estado.copy(carga = pronto.copy(valor = pronto.valor.copy(cofrinho = cofrinho)))
        }
    }

    // Objetivos

    private suspend fun recarregarObjetivos() {
        val objetivos = buscar(IdDoBloco.Objetivos) { financas.listarObjetivos() }
        estadoAtual.update { it.copy(objetivos = objetivos ?: it.objetivos.orEmpty()) }
    }

    fun novoObjetivo() = estadoAtual.update { it.copy(formulario = FormularioDeObjetivo(), erros = it.erros - IdDoBloco.Objetivos) }

    fun editarObjetivo(objetivo: ObjetivoDoCofrinho) = estadoAtual.update {
        it.copy(
            formulario = FormularioDeObjetivo(objetivo.id, objetivo.nome, objetivo.alvo, objetivo.prazo, objetivo.compartilhado == true),
            erros = it.erros - IdDoBloco.Objetivos,
        )
    }

    fun mudarFormulario(formulario: FormularioDeObjetivo) = estadoAtual.update { it.copy(formulario = formulario.copy(nome = formulario.nome.take(60))) }

    fun fecharFormulario() = estadoAtual.update { it.copy(formulario = null) }

    fun salvarObjetivo() {
        val estado = estadoAtual.value
        val formulario = estado.formulario ?: return
        val nome = formulario.nome.trim()
        val alvo = formulario.alvo
        when {
            nome.isEmpty() -> return mostrarErro(IdDoBloco.Objetivos, MensagemDeErro.DoApp(R.string.objetivo_sem_nome))
            alvo == null || alvo.signum() <= 0 -> return mostrarErro(IdDoBloco.Objetivos, MensagemDeErro.DoApp(R.string.objetivo_sem_alvo))
        }
        // Só vai quando a pessoa pode escolher: ausente, o backend deixa como está.
        val pedido = PedidoDeObjetivo(nome, alvo!!, formulario.prazo?.ifBlank { null }, formulario.compartilhado.takeIf { estado.podeCompartilhar })
        agir(Acao.Objetivo, IdDoBloco.Objetivos) {
            try {
                if (formulario.id == null) financas.criarObjetivo(pedido) else financas.alterarObjetivo(formulario.id, pedido)
            } catch (erro: ErroDaApi.DoServidor) {
                // O gratuito passou do limite (o backend responde PLANO_PREMIUM): o convite toma o lugar.
                if (erro.codigo == CODIGO_PREMIUM || erro.status == 403) estadoAtual.update { it.copy(limiteDoGratuito = true, formulario = null) }
                throw erro
            }
            estadoAtual.update { it.copy(formulario = null) }
            recarregarObjetivos()
        }
    }

    fun excluirObjetivo(id: String) = agir(Acao.Objetivo, IdDoBloco.Objetivos) {
        financas.excluirObjetivo(id)
        estadoAtual.update { it.copy(formulario = it.formulario?.takeIf { f -> f.id != id }, limiteDoGratuito = false) }
        recarregarObjetivos()
        // O que foi guardado nele volta para o cofrinho geral.
        recarregarCofrinho()
    }

    fun mudarValorDoObjetivo(id: String, valor: BigDecimal?) = estadoAtual.update { it.copy(valoresDosObjetivos = it.valoresDosObjetivos + (id to valor)) }

    fun depositarNoObjetivo(id: String) {
        val valor = estadoAtual.value.valoresDosObjetivos[id]
        if (valor == null || valor.signum() <= 0) return mostrarErro(IdDoBloco.Objetivos, MensagemDeErro.DoApp(R.string.deposito_invalido))
        agir(Acao.Objetivo, IdDoBloco.Objetivos) {
            financas.depositar(NovoDeposito(valor, hoje, id))
            estadoAtual.update { it.copy(valoresDosObjetivos = it.valoresDosObjetivos - id) }
            recarregarObjetivos()
            recarregarCofrinho()
        }
    }

    // Notas

    fun escreverNota() = estadoAtual.update { it.copy(notaNova = NotaNova()) }

    fun mudarNota(nota: NotaNova) = estadoAtual.update { it.copy(notaNova = NotaNova(nota.titulo.take(150), nota.texto.take(1500))) }

    fun cancelarNota() = estadoAtual.update { it.copy(notaNova = null, erros = it.erros - IdDoBloco.Notas) }

    fun salvarNota() {
        val nova = estadoAtual.value.notaNova ?: return
        val titulo = nova.titulo.trim()
        if (titulo.isEmpty()) return mostrarErro(IdDoBloco.Notas, MensagemDeErro.DoApp(R.string.nota_sem_titulo))
        agir(Acao.Nota, IdDoBloco.Notas) {
            val nota = financas.criarNota(NovaNota(titulo, nova.texto.trim().ifEmpty { null }))
            estadoAtual.update { it.copy(notas = listOf(nota) + it.notas.orEmpty(), notaNova = null) }
        }
    }

    fun excluirNota(id: String) = agir(Acao.Nota, IdDoBloco.Notas) {
        financas.excluirNota(id)
        estadoAtual.update { it.copy(notas = it.notas?.filter { nota -> nota.id != id }) }
    }

    // Bancos

    fun sincronizarBancos() = agirNosBancos { bancos.sincronizar() }

    fun desconectarBanco(conexaoId: String) = agirNosBancos { bancos.desconectar(conexaoId) }

    private fun agirNosBancos(chamada: suspend () -> List<ContaBancaria>) {
        if (Acao.Bancos in estadoAtual.value.ocupado) return
        estadoAtual.update { it.copy(ocupado = it.ocupado + Acao.Bancos, erroDosBancos = null) }
        viewModelScope.launch {
            try {
                val contas = chamada()
                estadoAtual.update { it.copy(bancos = contas) }
            } catch (erro: ErroDaApi) {
                estadoAtual.update { it.copy(erroDosBancos = mensagemDeErro(erro)) }
            } finally {
                estadoAtual.update { it.copy(ocupado = it.ocupado - Acao.Bancos) }
            }
        }
    }

    // Primeiros passos

    fun escolherMoeda(codigo: String) = estadoAtual.update { it.copy(moedaEscolhida = codigo) }

    /** Grava a moeda nas preferências da conta e marca o passo neste aparelho. */
    fun usarMoeda() {
        val estado = estadoAtual.value
        val codigo = estado.moedaEscolhida ?: estado.moeda ?: return
        val atuais = sessao.usuario?.preferencias
        agir(Acao.Moeda, IdDoBloco.Resumo) {
            val usuario = identidade.salvarPreferencias(
                PedidoDePreferencias(moeda = codigo, moedaConversao = atuais?.moedaConversao ?: "BRL", mostrarCotacao = atuais?.mostrarCotacao ?: true),
            )
            sessao.entrou(usuario)
            guardarGuia(estadoAtual.value.guia.copy(moeda = true))
        }
    }

    fun pularGuia() {
        viewModelScope.launch { guardarGuia(estadoAtual.value.guia.copy(fechado = true)) }
    }

    private suspend fun guardarGuia(guia: EstadoDoGuia) {
        estadoAtual.update { it.copy(guia = guia) }
        aparelho.guardarGuia(pessoa, guia.paraJson())
    }

    // Organizar

    fun moverBloco(bloco: IdDoBloco, passo: Int) = gravarOrdem(PainelOrganizado.mover(estadoAtual.value.blocos, bloco, passo))

    fun moverBlocoPara(bloco: IdDoBloco, indice: Int) = gravarOrdem(PainelOrganizado.moverPara(estadoAtual.value.blocos, bloco, indice))

    fun alternarBloco(bloco: IdDoBloco) = gravarOrdem(PainelOrganizado.alternar(estadoAtual.value.blocos, bloco))

    fun restaurarPainel() = gravarOrdem(PainelOrganizado.padrao)

    private fun gravarOrdem(blocos: List<BlocoDoPainel>) {
        if (blocos == estadoAtual.value.blocos) return
        estadoAtual.update { it.copy(blocos = blocos) }
        viewModelScope.launch { aparelho.guardarOrdem(pessoa, PainelOrganizado.paraJson(blocos)) }
    }

    private companion object {
        /** Plano.CODIGO_PREMIUM do backend: o recurso é do Premium. */
        const val CODIGO_PREMIUM = "PLANO_PREMIUM"
    }
}
