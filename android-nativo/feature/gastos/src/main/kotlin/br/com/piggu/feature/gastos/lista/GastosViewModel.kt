package br.com.piggu.feature.gastos.lista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.EscopoDoApp
import br.com.piggu.core.data.FilaDeGastos
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.RepositorioDoMes
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.mesAtual
import br.com.piggu.core.model.EdicaoDeGasto
import br.com.piggu.core.model.Gasto
import br.com.piggu.core.model.LinhaParaImportar
import br.com.piggu.core.model.PedidoDeExtrato
import br.com.piggu.core.model.PedidoDeImportacao
import br.com.piggu.core.model.PedidoDeNome
import br.com.piggu.core.model.PedidoDeRegra
import br.com.piggu.core.model.RegraDeCategoria
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.feature.gastos.R
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.util.Optional
import javax.inject.Inject

/** O extrato até 2 MB: o mesmo limite do servidor (importar-extrato.ts). */
const val TAMANHO_MAXIMO_DO_EXTRATO = 2_000_000

/**
 * O que a tela de Gastos pode pedir. O [GastosViewModel] faz tudo; as capturas passam um objeto
 * vazio (`object : AcoesDosGastos {}`).
 */
interface AcoesDosGastos {
    fun mudarMes(mes: String) {}
    fun recarregar() {}
    fun mudarBusca(busca: String) {}
    fun filtrarCategoria(categoria: String?) {}
    fun filtrarPagamento(filtro: FiltroDePagamento) {}
    fun limparFiltros() {}

    fun abrirEdicao(gasto: Gasto) {}
    fun mudarItem(item: String) {}
    fun mudarCategoria(categoria: String) {}
    fun mudarValor(valor: java.math.BigDecimal?) {}
    fun mudarCompartilhado(compartilhado: Boolean) {}
    fun salvarEdicao() {}
    fun fecharEdicao() {}

    /** Deslizou: a parcela com próximas pergunta antes; o resto some e espera o "Desfazer". */
    fun excluir(gasto: Gasto) {}
    fun apagar(gasto: Gasto, eAsProximas: Boolean) {}
    fun cancelarExclusao() {}
    fun desfazer() {}

    /** A Snackbar sumiu sem "Desfazer": agora vai ao servidor. */
    fun confirmarExclusao() {}

    suspend fun criarCategoria(nome: String): Result<List<String>> = Result.failure(UnsupportedOperationException())
    fun categoriasCriadas(categorias: List<String>) {}

    fun mudarTermoDaRegra(termo: String) {}
    fun mudarCategoriaDaRegra(categoria: String) {}
    fun criarRegra(termo: String, categoria: String) {}
    fun dispensarSugestao() {}
    fun excluirRegra(regra: RegraDeCategoria) {}

    /** O arquivo escolhido no seletor do sistema; nulo se não abriu. */
    fun lerExtrato(abrir: () -> InputStream?) {}
    fun alternarLinha(indice: Int) {}
    fun mudarCategoriaDaLinha(indice: Int, categoria: String) {}
    fun mudarContaDoExtrato(contaId: String) {}
    fun importarMarcados() {}
    fun cancelarPrevia() {}

    fun exportar() {}
    fun csvEntregue() {}

    fun esquecerRecusados() {}
    fun avisoMostrado() {}
}

/**
 * Gastos do mês (features/expenses e GastosView do iOS): a lista por dia com busca e filtros,
 * editar, apagar com "Desfazer", a divisão do mês, as regras de categoria, importar o extrato e
 * exportar o CSV. Lançar fica no + (o formulário de `formulario/`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GastosViewModel internal constructor(
    sessao: Sessao,
    private val financas: ServicoDeFinancas,
    private val doMes: RepositorioDoMes,
    listas: ListasParaLancar,
    private val fila: FilaDeGastos?,
    private val lancamentos: Lancamentos,
    conectado: StateFlow<Boolean>,
    relogio: Clock,
    /** A exclusão confirmada termina mesmo com a tela fechada. */
    private val escopoDoApp: CoroutineScope,
) : ViewModel(), AcoesDosGastos {

    @Inject constructor(
        sessao: Sessao,
        financas: ServicoDeFinancas,
        doMes: RepositorioDoMes,
        listas: ListasParaLancar,
        fila: Optional<FilaDeGastos>,
        lancamentos: Lancamentos,
        rede: MonitorDeRede,
        relogio: Clock,
        @EscopoDoApp escopoDoApp: CoroutineScope,
    ) : this(sessao, financas, doMes, listas, fila.orElse(null), lancamentos, rede.conectado, relogio, escopoDoApp)

    private val estadoAtual = MutableStateFlow(
        relogio.mesAtual().let { mes ->
            EstadoDosGastos(mes = mes, mesAtual = mes, moeda = sessao.usuario?.preferencias?.moeda, eu = sessao.usuario?.id)
        },
    )
    val estado: StateFlow<EstadoDosGastos> = estadoAtual.asStateFlow()

    private val recarga = MutableStateFlow(0)

    /** O gasto do link, aberto assim que a lista do mês dele chegar. */
    private var pedido: String? = null

    init {
        viewModelScope.launch {
            // Outro mês, algo lançado fora daqui (Lancamentos), "Tentar de novo" ou a rede que volta.
            combine(estadoAtual.map { it.mes }.distinctUntilChanged(), lancamentos.versao, recarga, conectado) { mes, _, _, _ -> mes }
                .collectLatest { mes ->
                    launch { carregarDivisao(mes) }
                    doMes.gastos(mes).collect { carga ->
                        estadoAtual.update { it.copy(carga = carga) }
                        abrirPedido()
                    }
                }
        }
        viewModelScope.launch { conectado.collect { c -> estadoAtual.update { it.copy(conectado = c) } } }
        viewModelScope.launch {
            listas.categorias().collect { carga ->
                (carga as? Carga.Pronto)?.valor?.categorias?.let { lista ->
                    estadoAtual.update { it.copy(categorias = lista, categoriaDaRegra = it.categoriaDaRegra.ifEmpty { lista.firstOrNull().orEmpty() }) }
                }
            }
        }
        viewModelScope.launch {
            listas.contas().collect { carga -> (carga as? Carga.Pronto)?.let { p -> estadoAtual.update { it.copy(contas = p.valor) } } }
        }
        viewModelScope.launch {
            listas.familia().collect { carga -> (carga as? Carga.Pronto)?.let { p -> estadoAtual.update { it.copy(membros = p.valor.membros) } } }
        }
        fila?.let { fila ->
            viewModelScope.launch { fila.aguardando.collect { lista -> estadoAtual.update { it.copy(aguardando = lista) } } }
            viewModelScope.launch { fila.recusados.collect { n -> estadoAtual.update { it.copy(recusados = n) } } }
        }
        carregarRegras()
    }

    /** Link `piggu://gastos?id=...&mes=...`: vai ao mês (sem ele, o atual) e abre o gasto. */
    fun abrir(id: String, mes: String?) {
        pedido = id
        estadoAtual.update { it.copy(mes = mes ?: it.mesAtual, destacado = id) }
        abrirPedido()
    }

    private fun abrirPedido() {
        val id = pedido ?: return
        val gasto = estadoAtual.value.gastos?.firstOrNull { it.id == id } ?: return
        pedido = null
        abrirEdicao(gasto)
    }

    private suspend fun carregarDivisao(mes: String) {
        val acerto = ouNulo { financas.acertoDoMes(mes) }.orEmpty()
        // Sem gasto dividido no mês não há acerto: poupa o pedido de quem paga quem.
        val transferencias = if (acerto.isEmpty()) emptyList() else ouNulo { financas.quemDeveAQuem(mes) }.orEmpty()
        estadoAtual.update { if (it.mes == mes) it.copy(acerto = acerto, transferencias = transferencias) else it }
    }

    private fun carregarRegras() {
        viewModelScope.launch { ouNulo { financas.regrasDeCategoria() }?.let { r -> estadoAtual.update { it.copy(regras = r) } } }
    }

    // Lista e filtros

    override fun mudarMes(mes: String) {
        pedido = null
        estadoAtual.update { it.copy(mes = mes, destacado = null, acerto = emptyList(), transferencias = emptyList()) }
    }

    override fun recarregar() = recarga.update { it + 1 }
    override fun mudarBusca(busca: String) = estadoAtual.update { it.copy(busca = busca) }
    override fun filtrarCategoria(categoria: String?) = estadoAtual.update { it.copy(filtroDeCategoria = categoria) }
    override fun filtrarPagamento(filtro: FiltroDePagamento) = estadoAtual.update { it.copy(filtroDePagamento = filtro) }
    override fun limparFiltros() =
        estadoAtual.update { it.copy(busca = "", filtroDeCategoria = null, filtroDePagamento = FiltroDePagamento.Todas) }

    // Editar (item, categoria, valor e, de quem lançou, o compartilhado)

    override fun abrirEdicao(gasto: Gasto) = estadoAtual.update {
        it.copy(
            edicao = EdicaoDoGasto(
                gasto, gasto.item, gasto.categoria, gasto.valor, gasto.compartilhado == true,
                podeCompartilhar = it.ehMeu(gasto) && it.comCompanhia,
            ),
        )
    }

    private fun editar(mudar: (EdicaoDoGasto) -> EdicaoDoGasto) =
        estadoAtual.update { e -> e.copy(edicao = e.edicao?.let { mudar(it).copy(erro = null) }) }

    override fun mudarItem(item: String) = editar { it.copy(item = item.take(200)) }
    override fun mudarCategoria(categoria: String) = editar { it.copy(categoria = categoria) }
    override fun mudarValor(valor: java.math.BigDecimal?) = editar { it.copy(valor = valor) }
    override fun mudarCompartilhado(compartilhado: Boolean) = editar { it.copy(compartilhado = compartilhado) }
    override fun fecharEdicao() = estadoAtual.update { it.copy(edicao = null) }

    override fun salvarEdicao() {
        val edicao = estadoAtual.value.edicao ?: return
        if (edicao.salvando) return
        val item = edicao.item.trim()
        val valor = edicao.valor
        if (item.isEmpty() || valor == null || valor.signum() < 0) {
            estadoAtual.update { it.copy(edicao = edicao.copy(erro = MensagemDeErro.DoApp(R.string.gastos_confira_nome_e_valor))) }
            return
        }
        estadoAtual.update { it.copy(edicao = edicao.copy(salvando = true, erro = null)) }
        viewModelScope.launch {
            try {
                val salvo = financas.editarGasto(
                    edicao.gasto.id,
                    EdicaoDeGasto(item, edicao.categoria, valor, edicao.compartilhado.takeIf { edicao.podeCompartilhar }),
                )
                estadoAtual.update {
                    it.copy(
                        edicao = null,
                        sugestao = if (salvo.categoria != edicao.gasto.categoria) RegraSugerida(salvo.item, salvo.categoria) else it.sugestao,
                    )
                }
                lancamentos.avisar()
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(edicao = it.edicao?.copy(salvando = false, erro = mensagemDeErro(falha))) }
            }
        }
    }

    // Apagar com "Desfazer"

    override fun excluir(gasto: Gasto) {
        if (gasto.seguintes() > 0) estadoAtual.update { it.copy(confirmando = gasto) } else apagar(gasto, eAsProximas = false)
    }

    override fun apagar(gasto: Gasto, eAsProximas: Boolean) {
        confirmarExclusao()
        estadoAtual.update {
            it.copy(confirmando = null, edicao = null, ocultos = it.ocultos + gasto.id, exclusao = ExclusaoPendente(gasto, eAsProximas))
        }
    }

    override fun cancelarExclusao() = estadoAtual.update { it.copy(confirmando = null) }

    override fun desfazer() = estadoAtual.update { e ->
        e.exclusao?.let { e.copy(exclusao = null, ocultos = e.ocultos - it.gasto.id) } ?: e
    }

    override fun confirmarExclusao() {
        val pendente = estadoAtual.value.exclusao ?: return
        estadoAtual.update { it.copy(exclusao = null) }
        escopoDoApp.launch {
            try {
                // O filtro só vai quando é para apagar as próximas (nulo não manda nada).
                financas.excluirGasto(pendente.gasto.id, true.takeIf { pendente.eAsProximas })
                lancamentos.avisar()
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(ocultos = it.ocultos - pendente.gasto.id, erro = mensagemDeErro(falha)) }
            }
        }
    }

    override fun onCleared() = confirmarExclusao()

    // Categorias e regras

    override suspend fun criarCategoria(nome: String): Result<List<String>> =
        try {
            Result.success(financas.criarCategoria(PedidoDeNome(nome)).categorias)
        } catch (falha: ErroDaApi) {
            Result.failure(falha)
        }

    override fun categoriasCriadas(categorias: List<String>) = estadoAtual.update { it.copy(categorias = categorias) }
    override fun mudarTermoDaRegra(termo: String) = estadoAtual.update { it.copy(termoDaRegra = termo.take(100)) }
    override fun mudarCategoriaDaRegra(categoria: String) = estadoAtual.update { it.copy(categoriaDaRegra = categoria) }
    override fun dispensarSugestao() = estadoAtual.update { it.copy(sugestao = null) }

    override fun criarRegra(termo: String, categoria: String) {
        if (termo.isBlank() || categoria.isEmpty()) {
            estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(R.string.regras_digite_termo)) }
            return
        }
        viewModelScope.launch {
            try {
                val regra = financas.definirRegra(PedidoDeRegra(termo.trim(), categoria))
                estadoAtual.update {
                    it.copy(sugestao = null, termoDaRegra = "", aviso = Mensagem(R.string.regras_salva, listOf(regra.termo, regra.categoria)))
                }
                carregarRegras()
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
            }
        }
    }

    override fun excluirRegra(regra: RegraDeCategoria) {
        viewModelScope.launch {
            try {
                financas.excluirRegra(regra.id)
                estadoAtual.update { e -> e.copy(regras = e.regras.filterNot { it.id == regra.id }) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
            }
        }
    }

    // Importar e exportar

    override fun lerExtrato(abrir: () -> InputStream?) {
        estadoAtual.update { it.copy(ocupado = true, previa = null) }
        viewModelScope.launch {
            val bytes = try {
                withContext(Dispatchers.IO) { abrir()?.use { ateOLimite(it) } }
            } catch (_: IOException) {
                null
            }
            val erro = when {
                bytes == null -> R.string.importar_nao_leu
                bytes.size > TAMANHO_MAXIMO_DO_EXTRATO -> R.string.importar_grande_demais
                else -> null
            }
            if (erro != null || bytes == null) {
                estadoAtual.update { it.copy(ocupado = false, erro = MensagemDeErro.DoApp(erro ?: R.string.importar_nao_leu)) }
                return@launch
            }
            try {
                val linhas = financas.previaDoExtrato(PedidoDeExtrato(textoDoExtrato(bytes)))
                estadoAtual.update {
                    it.copy(
                        ocupado = false,
                        previa = linhas.map { l -> LinhaDaPrevia(l, marcada = !l.jaImportada) }.ifEmpty { null },
                        erro = if (linhas.isEmpty()) MensagemDeErro.DoApp(R.string.importar_nada_no_extrato) else null,
                    )
                }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(ocupado = false, erro = mensagemDeErro(falha)) }
            }
        }
    }

    private fun mudarPrevia(indice: Int, mudar: (LinhaDaPrevia) -> LinhaDaPrevia) = estadoAtual.update { e ->
        e.copy(previa = e.previa?.mapIndexed { i, l -> if (i == indice) mudar(l) else l })
    }

    override fun alternarLinha(indice: Int) = mudarPrevia(indice) { it.copy(marcada = !it.marcada) }
    override fun mudarCategoriaDaLinha(indice: Int, categoria: String) =
        mudarPrevia(indice) { it.copy(linha = it.linha.copy(categoria = categoria)) }
    override fun mudarContaDoExtrato(contaId: String) = estadoAtual.update { it.copy(contaDoExtrato = contaId) }
    override fun cancelarPrevia() = estadoAtual.update { it.copy(previa = null) }

    override fun importarMarcados() {
        val atual = estadoAtual.value
        val marcadas = atual.previa.orEmpty().filter { it.marcada }.map { it.linha }
        if (atual.ocupado || marcadas.isEmpty()) return
        estadoAtual.update { it.copy(ocupado = true) }
        viewModelScope.launch {
            try {
                val (importados, pulados) = financas.importarExtrato(
                    PedidoDeImportacao(
                        marcadas.map { LinhaParaImportar(it.data, it.descricao, it.valor, it.idExterno, it.categoria) },
                        atual.contaDoExtrato.ifEmpty { null },
                    ),
                )
                val aviso = if (pulados > 0) Mensagem(R.string.importar_importados_e_pulados, listOf(importados, pulados))
                else Mensagem(R.string.importar_importados, listOf(importados))
                estadoAtual.update { it.copy(ocupado = false, previa = null, aviso = aviso) }
                lancamentos.avisar()
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(ocupado = false, erro = mensagemDeErro(falha)) }
            }
        }
    }

    override fun exportar() {
        val mes = estadoAtual.value.mes
        viewModelScope.launch {
            try {
                // O corpo vem em streaming: ler é ir à rede, fora da thread principal.
                val bytes = withContext(Dispatchers.IO) { financas.exportarGastos(mes).use { it.bytes() } }
                estadoAtual.update { it.copy(csv = CsvDoMes("piggu-gastos-$mes.csv", bytes)) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
            } catch (falha: IOException) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(ErroDaApi.de(falha))) }
            }
        }
    }

    override fun csvEntregue() = estadoAtual.update { it.copy(csv = null) }

    override fun esquecerRecusados() {
        viewModelScope.launch { fila?.esquecerRecusados() }
    }

    /** Some o que a Snackbar mostrou: o aviso, que vem antes, ou o erro. */
    override fun avisoMostrado() = estadoAtual.update { if (it.aviso != null) it.copy(aviso = null) else it.copy(erro = null) }
}

private suspend fun <T> ouNulo(bloco: suspend () -> T): T? =
    try {
        bloco()
    } catch (_: ErroDaApi) {
        null
    }

/** Lê até um byte além do limite: o bastante para saber que passou. */
private fun ateOLimite(entrada: InputStream): ByteArray {
    val saida = ByteArrayOutputStream()
    val pedaco = ByteArray(8_192)
    while (saida.size() <= TAMANHO_MAXIMO_DO_EXTRATO) {
        val lidos = entrada.read(pedaco)
        if (lidos < 0) break
        saida.write(pedaco, 0, lidos)
    }
    return saida.toByteArray()
}

/** OFX de banco brasileiro costuma vir em Latin-1: se o UTF-8 der caractere quebrado, relê assim. */
internal fun textoDoExtrato(bytes: ByteArray): String =
    String(bytes, Charsets.UTF_8).takeUnless { '�' in it } ?: String(bytes, Charsets.ISO_8859_1)

/**
 * A fila sem internet é do formulário (`formulario/`), que a liga no Hilt.
 * shortcut: opcional para este módulo compilar sem ela; com a fila ligada, injete [FilaDeGastos] direto.
 */
@Module
@InstallIn(SingletonComponent::class)
interface FilaOpcional {
    @BindsOptionalOf
    fun fila(): FilaDeGastos
}
