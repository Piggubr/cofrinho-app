package br.com.piggu.feature.gastos.formulario

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.FilaDeGastos
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.VERSAO_DOS_TERMOS
import br.com.piggu.core.data.ehPremium
import br.com.piggu.core.model.AlertaDoOrcamento
import br.com.piggu.core.model.ConferenciaDePreco
import br.com.piggu.core.model.ContaOuCartao
import br.com.piggu.core.model.ItemDeGasto
import br.com.piggu.core.model.MembroDaFamilia
import br.com.piggu.core.model.NovoLancamento
import br.com.piggu.core.model.PedidoDeLeitura
import br.com.piggu.core.model.PedidoDeNome
import br.com.piggu.core.model.ReciboLido
import br.com.piggu.core.model.TipoDeConta
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.formatos.chaveDoMes
import br.com.piggu.feature.gastos.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.util.Base64
import java.util.UUID
import javax.inject.Inject

/** As moedas oferecidas para a compra, depois da moeda da pessoa (formulario-de-gasto.ts). */
private val MOEDAS_DA_COMPRA = listOf("BRL", "USD", "EUR", "GBP", "ARS", "CLP", "UYU", "JPY")

/** No cartão a compra entra na fatura; no débito/Pix só desconta da conta, sempre à vista. */
enum class FormaDePagamento { CARTAO, DEBITO }

enum class ModoDaCompra { VISTA, PARCELADO, RECORRENTE }

/** Campo que faltou no último "Lançar": fica com a borda vermelha. */
enum class CampoQueFaltou { CONTA, PARCELAS }

/** Uma linha do gasto. [chave] identifica a linha enquanto o formulário está aberto. */
data class ItemDoFormulario(
    val chave: Int,
    val item: String = "",
    /** Vazia é "Automática": o backend escolhe pelas regras. */
    val categoria: String = "",
    val valor: BigDecimal? = null,
    /** Preço acima da média deste item (conferência de preço). */
    val precoAcima: ConferenciaDePreco? = null,
)

/** A mensagem para quem abriu o formulário, com os argumentos do texto. */
data class AvisoDoLancamento(@param:StringRes val texto: Int, val argumentos: List<String> = emptyList())

data class EstadoDoFormulario(
    val hoje: LocalDate,
    /** A moeda da pessoa (preferencias.moeda). */
    val moeda: String,
    val data: LocalDate = hoje,
    val estabelecimento: String = "",
    val itens: List<ItemDoFormulario> = listOf(ItemDoFormulario(0)),
    val categorias: List<String> = emptyList(),
    val contas: List<ContaOuCartao> = emptyList(),
    val forma: FormaDePagamento = FormaDePagamento.DEBITO,
    val contaId: String = "",
    val modo: ModoDaCompra = ModoDaCompra.VISTA,
    /** Como a pessoa digitou. */
    val parcelas: String = "1",
    val moedaDaCompra: String = moeda,
    val cotacao: BigDecimal? = null,
    val membros: List<MembroDaFamilia> = emptyList(),
    val dividirCom: List<String> = emptyList(),
    /** Escolha da pessoa; nula segue o cartão (o do casal compartilha) e o padrão do perfil. */
    val compartilhado: Boolean? = null,
    val compartilharPorPadrao: Boolean = false,
    /** Lançamento rápido: data, estabelecimento, moeda e divisão ficam recolhidos. */
    val maisOpcoes: Boolean = false,
    val semInternet: Boolean = false,
    val salvando: Boolean = false,
    val erro: MensagemDeErro? = null,
    val faltou: CampoQueFaltou? = null,
    /** [ItemDoFormulario.chave] da linha que faltou. */
    val itemComErro: Int? = null,
    /** Lançou (ou guardou sem internet): a tela entrega a mensagem e chama [FormularioDeGastoViewModel.avisoEntregue]. */
    val lancado: AvisoDoLancamento? = null,
    /** Reduzindo a foto do recibo ou esperando o servidor ler. */
    val lendoRecibo: Boolean = false,
    /** Leituras grátis que sobram no mês; nula no Premium ou antes de saber. */
    val leiturasRestantes: Int? = null,
    /** A foto do recibo em JPEG: a prévia na conferência e o reenvio depois de autorizar a IA. */
    val fotoDoRecibo: ByteArray? = null,
    /** O servidor só lê pela IA com autorização: a mensagem dele, e a foto espera em [fotoDoRecibo]. */
    val pedidoDeIa: MensagemDeErro? = null,
    /** Recibo lido, em conferência até a pessoa confirmar ("Conferir recibo"). */
    val recibo: ReciboLido? = null,
    val itensDoRecibo: List<ItemDoFormulario> = emptyList(),
    /** O recibo confirmado que preencheu o formulário: vai no Lançar, com a origem "Foto". */
    val reciboId: String? = null,
) {
    val cartoes: List<ContaOuCartao> get() = contas.filter { it.tipo == TipoDeConta.CARTAO }
    val bancos: List<ContaOuCartao> get() = contas.filter { it.tipo == TipoDeConta.CONTA }
    val outraMoeda: Boolean get() = moedaDaCompra != moeda
    val moedasDaCompra: List<String> get() = (listOf(moeda) + MOEDAS_DA_COMPRA).distinct()
    val ehHoje: Boolean get() = data == hoje
    val diaDaCompra: Int get() = data.dayOfMonth
    val podeDividir: Boolean get() = membros.size > 1

    /** Valor na moeda da pessoa pela cotação do dia; nulo enquanto ela não chega. */
    fun convertido(valor: BigDecimal?): BigDecimal? = cotacao?.let { taxa -> valor?.multiply(taxa)?.setScale(2, RoundingMode.HALF_UP) }

    /** A soma dos itens, na moeda da compra. */
    val total: BigDecimal get() = itens.mapNotNull { it.valor }.fold(BigDecimal.ZERO, BigDecimal::add)
    val totalConvertido: BigDecimal? get() = if (outraMoeda) convertido(total) else null
    val totalDoRecibo: BigDecimal get() = itensDoRecibo.mapNotNull { it.valor }.fold(BigDecimal.ZERO, BigDecimal::add)

    val valorDaParcela: BigDecimal?
        get() {
            val quantas = parcelas.toIntOrNull()?.takeIf { it >= 2 } ?: return null
            return total.takeIf { it.signum() > 0 }?.divide(quantas.toBigDecimal(), 2, RoundingMode.DOWN)
        }

    val compartilhadoEfetivo: Boolean
        get() = compartilhado ?: (contas.firstOrNull { it.id == contaId }?.compartilhado == true || compartilharPorPadrao)

    /** A fatura em que a compra cai (mês AAAA-MM e vencimento AAAA-MM-DD). */
    val fatura: Pair<String, String>?
        get() {
            val cartao = cartoes.firstOrNull { it.id == contaId }
            if (forma != FormaDePagamento.CARTAO || cartao?.fechamento == null || cartao.vencimento == null) return null
            return faturaDaCompra(cartao.fechamento!!, cartao.vencimento!!, data)
        }
}

/**
 * O formulário de gasto (formulario-de-gasto.ts e LancarGastoView.swift), o mesmo do + e da aba
 * de Gastos. Sem internet (o [MonitorDeRede] desligado ou a chamada falhando sem rede) o gasto vai
 * para a [FilaDeGastos]. O idDoEnvio é novo a cada toque em "Lançar" (D-073).
 */
@HiltViewModel
class FormularioDeGastoViewModel @Inject constructor(
    @ApplicationContext contexto: Context,
    sessao: Sessao,
    private val financas: ServicoDeFinancas,
    listas: ListasParaLancar,
    private val fila: FilaDeGastos,
    rede: MonitorDeRede,
    private val lancamentos: Lancamentos,
    relogio: Clock,
) : ViewModel() {
    private val usuario = sessao.usuario
    private val estadoAtual = MutableStateFlow(
        EstadoDoFormulario(
            hoje = LocalDate.now(relogio),
            moeda = usuario?.preferencias?.moeda ?: MOEDA_SEM_PREFERENCIA,
            compartilharPorPadrao = usuario?.compartilhamento?.compartilharPorPadrao == true,
        ),
    )
    val estado: StateFlow<EstadoDoFormulario> = estadoAtual.asStateFlow()

    // A última conta usada vira a padrão do próximo lançamento, neste aparelho (piggu_conta_padrao do site).
    private val preferencias = contexto.getSharedPreferences("piggu_formulario_de_gasto", Context.MODE_PRIVATE)
    private var proximaChave = 1
    private var buscaDaCotacao: Job? = null

    init {
        viewModelScope.launch { rede.conectado.collect { conectado -> estadoAtual.update { it.copy(semInternet = !conectado) } } }
        viewModelScope.launch {
            listas.categorias().collect { carga ->
                if (carga is Carga.Pronto) estadoAtual.update { it.copy(categorias = carga.valor.categorias) }
            }
        }
        viewModelScope.launch {
            var primeira = true
            listas.contas().collect { carga ->
                if (carga !is Carga.Pronto) return@collect
                estadoAtual.update { it.copy(contas = carga.valor) }
                if (primeira) {
                    primeira = false
                    val padrao = carga.valor.firstOrNull { it.id == preferencias.getString(CONTA_PADRAO, null) }
                    trocarForma(if (padrao?.tipo == TipoDeConta.CARTAO) FormaDePagamento.CARTAO else FormaDePagamento.DEBITO, padrao?.id)
                }
            }
        }
        viewModelScope.launch {
            listas.familia().collect { carga ->
                if (carga is Carga.Pronto) estadoAtual.update { it.copy(membros = carga.valor.membros) }
            }
        }
        if (usuario?.ehPremium != true) {
            viewModelScope.launch {
                // O contador só informa: sem ele o botão continua funcionando.
                try {
                    val uso = financas.usoDeLeituras()
                    estadoAtual.update { it.copy(leiturasRestantes = uso.restantes) }
                } catch (_: ErroDaApi) {
                }
            }
        }
    }

    /** Atalho e link (piggu://gasto?valor=12.50): o valor já dito. */
    fun preencherValor(valor: BigDecimal?) {
        if (valor != null && valor.signum() > 0) mudarItem(estadoAtual.value.itens.first().chave) { it.copy(valor = valor) }
    }

    fun mudarData(data: LocalDate) = estadoAtual.update { it.copy(data = data) }
    fun mudarEstabelecimento(texto: String) = estadoAtual.update { it.copy(estabelecimento = texto.take(200)) }
    fun alternarMaisOpcoes() = estadoAtual.update { it.copy(maisOpcoes = !it.maisOpcoes) }

    fun mudarDescricao(chave: Int, texto: String) = mudarItem(chave) { it.copy(item = texto.take(200)) }
    fun mudarCategoria(chave: Int, categoria: String) = mudarItem(chave) { it.copy(categoria = categoria) }
    fun mudarValor(chave: Int, valor: BigDecimal?) = mudarItem(chave) { it.copy(valor = valor) }

    fun adicionarItem() = estadoAtual.update {
        if (it.itens.size >= MAXIMO_DE_ITENS) it else it.copy(itens = it.itens + ItemDoFormulario(proximaChave++))
    }

    /** Tira a linha do formulário (fica pelo menos uma) ou da conferência do recibo (pode tirar todas). */
    fun removerItem(chave: Int) = estadoAtual.update { atual ->
        when {
            atual.itensDoRecibo.any { it.chave == chave } -> atual.copy(itensDoRecibo = atual.itensDoRecibo.filterNot { it.chave == chave })
            atual.itens.size <= 1 -> atual
            else -> atual.copy(itens = atual.itens.filterNot { it.chave == chave }, itemComErro = null)
        }
    }

    /** Recebe as categorias depois de criar uma (o CampoDeCategoria já escolhe a nova). */
    fun categoriasCriadas(lista: List<String>) = estadoAtual.update { it.copy(categorias = lista) }

    /** Cria a categoria no servidor; a falha vem com o [ErroDaApi] para a tela escrever. */
    suspend fun criarCategoria(nome: String): Result<List<String>> =
        try {
            Result.success(financas.criarCategoria(PedidoDeNome(nome)).categorias)
        } catch (erro: ErroDaApi) {
            Result.failure(erro)
        }

    /** Troca cartão por débito/Pix; com uma conta só daquele tipo, ela já vem escolhida. Débito/Pix é sempre à vista. */
    fun trocarForma(forma: FormaDePagamento, preferida: String? = null) = estadoAtual.update { atual ->
        val opcoes = if (forma == FormaDePagamento.CARTAO) atual.cartoes else atual.bancos
        val escolhida = opcoes.firstOrNull { it.id == (preferida ?: atual.contaId) }
        val conta = escolhida?.id ?: opcoes.singleOrNull()?.id.orEmpty()
        val modo = if (forma == FormaDePagamento.DEBITO) ModoDaCompra.VISTA else atual.modo
        atual.copy(forma = forma, contaId = conta, modo = modo, parcelas = parcelasDo(modo, atual), faltou = null)
    }

    fun escolherConta(id: String) = estadoAtual.update { it.copy(contaId = id, faltou = null) }

    /** Parcelado começa em 2x. */
    fun trocarModo(modo: ModoDaCompra) = estadoAtual.update { it.copy(modo = modo, parcelas = if (modo == ModoDaCompra.PARCELADO) "2" else "1", faltou = null) }

    fun mudarParcelas(texto: String) = estadoAtual.update { it.copy(parcelas = texto.filter(Char::isDigit).take(2), faltou = null) }

    fun alternarDivisao(pessoa: String) = estadoAtual.update {
        it.copy(dividirCom = if (pessoa in it.dividirCom) it.dividirCom - pessoa else it.dividirCom + pessoa)
    }

    fun mudarCompartilhado(compartilhado: Boolean) = estadoAtual.update { it.copy(compartilhado = compartilhado) }

    /** Compra em outra moeda: busca a cotação para a moeda da pessoa. */
    fun trocarMoeda(codigo: String) {
        buscaDaCotacao?.cancel()
        estadoAtual.update { it.copy(moedaDaCompra = codigo, cotacao = null) }
        val moeda = estadoAtual.value.moeda
        if (codigo == moeda) return
        buscaDaCotacao = viewModelScope.launch {
            try {
                val cotacao = financas.consultarCotacao(codigo, moeda)
                estadoAtual.update { it.copy(cotacao = cotacao.taxa) }
            } catch (erro: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(erro)) }
            }
        }
    }

    /** Ao sair do valor: avisa se o preço passou da média do item (só na moeda da pessoa e com rede). */
    fun conferirPreco(chave: Int) {
        val atual = estadoAtual.value
        val linha = atual.itens.firstOrNull { it.chave == chave } ?: return
        val item = linha.item.trim()
        val valor = linha.valor
        if (item.isEmpty() || valor == null || valor.signum() <= 0 || atual.outraMoeda || atual.semInternet) {
            mudarItem(chave) { it.copy(precoAcima = null) }
            return
        }
        viewModelScope.launch {
            // O aviso de preço é extra: sem ele o lançamento segue.
            val conferencia = try {
                financas.conferirPreco(item, valor.toPlainString()).body()
            } catch (_: ErroDaApi) {
                null
            }
            mudarItem(chave) { it.copy(precoAcima = conferencia?.takeIf { c -> c.acima }) }
        }
    }

    fun lancar() {
        val atual = estadoAtual.value
        if (atual.salvando) return
        estadoAtual.update { it.copy(faltou = null, itemComErro = null) }
        atual.itens.firstOrNull { it.item.isBlank() }?.let { return falhar(R.string.formulario_digite_o_item, item = it.chave) }
        atual.itens.firstOrNull { it.valor == null || it.valor.signum() < 0 || it.valor > VALOR_MAXIMO }
            ?.let { return falhar(R.string.formulario_valor_invalido, item = it.chave) }
        if (atual.outraMoeda && atual.cotacao == null) return falhar(R.string.formulario_aguarde_a_cotacao)
        if (atual.forma == FormaDePagamento.CARTAO && atual.cartoes.isEmpty()) return falhar(R.string.formulario_cadastre_o_cartao)
        if (atual.contaId.isEmpty() && (atual.forma == FormaDePagamento.CARTAO || atual.bancos.isNotEmpty())) {
            val texto = if (atual.forma == FormaDePagamento.CARTAO) R.string.formulario_escolha_o_cartao else R.string.formulario_escolha_a_conta
            return falhar(texto, CampoQueFaltou.CONTA)
        }
        val parcelas = if (atual.modo == ModoDaCompra.PARCELADO) atual.parcelas.toIntOrNull() ?: 0 else 1
        if (parcelas !in 1..48 || (atual.modo == ModoDaCompra.PARCELADO && parcelas < 2)) {
            return falhar(R.string.formulario_parcelas_invalidas, CampoQueFaltou.PARCELAS)
        }

        val pedido = NovoLancamento(
            data = atual.data.toString(),
            estabelecimento = atual.estabelecimento.trim().ifEmpty { null },
            reciboId = atual.reciboId,
            origem = if (atual.reciboId != null) "Foto" else "Manual",
            itens = atual.itens.map { linha ->
                ItemDeGasto(
                    item = linha.item.trim(),
                    categoria = linha.categoria.ifEmpty { null },
                    valor = if (atual.outraMoeda) atual.convertido(linha.valor)!! else linha.valor!!,
                    // Sem o campo Tipo na tela: o recorrente vira conta fixa, o resto é variável.
                    tipo = if (atual.modo == ModoDaCompra.RECORRENTE) "Fixo" else "Variavel",
                    moedaOriginal = atual.moedaDaCompra.takeIf { atual.outraMoeda },
                    valorOriginal = linha.valor.takeIf { atual.outraMoeda },
                )
            },
            contaId = atual.contaId.ifEmpty { null },
            parcelas = parcelas.takeIf { it > 1 },
            dividirCom = atual.dividirCom.ifEmpty { null },
            recorrente = true.takeIf { atual.modo == ModoDaCompra.RECORRENTE },
            compartilhado = atual.compartilhadoEfetivo,
            // Sem resposta o servidor pode ter gravado: a fila reenvia com o mesmo id.
            idDoEnvio = UUID.randomUUID().toString(),
        )
        estadoAtual.update { it.copy(salvando = true, erro = null) }
        viewModelScope.launch {
            if (atual.semInternet) return@launch guardarNaFila(pedido)
            try {
                val salvos = financas.lancarGastos(pedido)
                lancamentos.avisar()
                limpar(avisoDoOrcamento(salvos.firstOrNull()?.categoria, chaveDoMes(atual.data)))
            } catch (_: ErroDaApi.SemInternet) {
                guardarNaFila(pedido)
            } catch (erro: ErroDaApi) {
                estadoAtual.update { it.copy(salvando = false, erro = mensagemDeErro(erro)) }
            }
        }
    }

    fun avisoEntregue() = estadoAtual.update { it.copy(lancado = null) }

    // Na fila do mercado, sem sinal: guarda e manda quando a conexão voltar.
    private suspend fun guardarNaFila(pedido: NovoLancamento) {
        fila.guardar(pedido)
        limpar(AvisoDoLancamento(R.string.formulario_guardado_sem_internet))
    }

    /** Depois de lançar: se a categoria passou de 80% do orçamento do mês, a mensagem avisa. */
    private suspend fun avisoDoOrcamento(categoria: String?, mes: String): AvisoDoLancamento {
        val simples = AvisoDoLancamento(R.string.formulario_gasto_lancado)
        categoria ?: return simples
        val orcamento = try {
            financas.orcamentos(mes).firstOrNull { it.categoria == categoria && it.alerta != AlertaDoOrcamento.OK }
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (_: Exception) {
            null // O aviso do orçamento é extra: o lançamento já deu certo.
        } ?: return simples
        val percentual = orcamento.percentual.stripTrailingZeros().toPlainString()
        val texto = if (orcamento.alerta == AlertaDoOrcamento.ESTOUROU) R.string.formulario_lancado_estourou else R.string.formulario_lancado_atencao
        return AvisoDoLancamento(texto, listOf(categoria, percentual))
    }

    /** Fica a data, a moeda, a conta e a categoria do primeiro item, para o próximo. */
    private fun limpar(aviso: AvisoDoLancamento) {
        val atual = estadoAtual.value
        preferencias.edit { if (atual.contaId.isEmpty()) remove(CONTA_PADRAO) else putString(CONTA_PADRAO, atual.contaId) }
        estadoAtual.update {
            it.copy(
                estabelecimento = "",
                itens = listOf(ItemDoFormulario(proximaChave++, categoria = it.itens.first().categoria)),
                modo = ModoDaCompra.VISTA,
                parcelas = "1",
                dividirCom = emptyList(),
                compartilhado = null,
                reciboId = null,
                salvando = false,
                lancado = aviso,
            )
        }
    }

    /**
     * A foto do recibo, do recorte, da câmera ou da galeria ([abrir] dá um fluxo novo dela a cada
     * chamada). Vai reduzida ao servidor, que lê e devolve os itens; nada é gravado ainda.
     */
    fun lerFotoDoRecibo(abrir: () -> InputStream?) {
        if (estadoAtual.value.lendoRecibo) return
        estadoAtual.update { it.copy(lendoRecibo = true, erro = null) }
        viewModelScope.launch {
            val jpeg = withContext(Dispatchers.IO) {
                try {
                    reduzirFotoDoRecibo(abrir)
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }
            if (jpeg == null) {
                estadoAtual.update { it.copy(lendoRecibo = false, erro = MensagemDeErro.DoApp(R.string.recibo_foto_ilegivel)) }
                return@launch
            }
            estadoAtual.update { it.copy(fotoDoRecibo = jpeg) }
            lerRecibo(jpeg, autorizo = false)
        }
    }

    /** A pessoa leu o aviso e autorizou: a mesma foto vai de novo, agora podendo ir à IA. */
    fun autorizarIa() {
        val atual = estadoAtual.value
        val foto = atual.fotoDoRecibo ?: return
        if (atual.pedidoDeIa == null) return
        estadoAtual.update { it.copy(pedidoDeIa = null, lendoRecibo = true) }
        viewModelScope.launch { lerRecibo(foto, autorizo = true) }
    }

    /** "Agora não": a foto não vai à IA e sai do aparelho. */
    fun recusarIa() = estadoAtual.update { it.copy(pedidoDeIa = null, fotoDoRecibo = null) }

    private suspend fun lerRecibo(jpeg: ByteArray, autorizo: Boolean) {
        try {
            val lido = financas.lerRecibo(
                PedidoDeLeitura(
                    imageBase64 = Base64.getEncoder().encodeToString(jpeg),
                    mimeType = "image/jpeg",
                    autorizoIa = true.takeIf { autorizo },
                    // Gravada pelo servidor junto com a leitura, como no site (VERSAO_DO_AVISO).
                    versaoDoAviso = VERSAO_DOS_TERMOS.takeIf { autorizo },
                ),
            )
            val itens = lido.itens.take(MAXIMO_DE_ITENS).map { ItemDoFormulario(proximaChave++, it.item.take(200), it.categoria, it.valor) }
            estadoAtual.update {
                it.copy(lendoRecibo = false, leiturasRestantes = lido.leiturasRestantes, recibo = lido, itensDoRecibo = itens)
            }
        } catch (falha: ErroDaApi) {
            val pedeConsentimento = falha is ErroDaApi.DoServidor && falha.codigo == "CONSENTIMENTO_NECESSARIO"
            estadoAtual.update {
                if (pedeConsentimento) {
                    it.copy(lendoRecibo = false, pedidoDeIa = mensagemDeErro(falha))
                } else {
                    it.copy(lendoRecibo = false, erro = mensagemDeErro(falha), fotoDoRecibo = null)
                }
            }
        }
    }

    /** Os itens conferidos preenchem o formulário (com o lugar e a data do cupom); só o Lançar grava. */
    fun confirmarRecibo() {
        val atual = estadoAtual.value
        val lido = atual.recibo ?: return
        val itens = atual.itensDoRecibo.filter { it.item.isNotBlank() && it.valor != null && it.valor.signum() >= 0 && it.valor <= VALOR_MAXIMO }
        if (itens.isEmpty()) return falhar(R.string.recibo_nenhum_item_valido)
        estadoAtual.update {
            it.copy(
                itens = itens,
                estabelecimento = lido.estabelecimento.take(200),
                data = runCatching { LocalDate.parse(lido.data.take(10)) }.getOrDefault(it.data),
                reciboId = lido.reciboId,
                recibo = null,
                itensDoRecibo = emptyList(),
                fotoDoRecibo = null,
                erro = null,
            )
        }
    }

    fun descartarRecibo() = estadoAtual.update { it.copy(recibo = null, itensDoRecibo = emptyList(), fotoDoRecibo = null, erro = null) }

    private fun falhar(@StringRes texto: Int, faltou: CampoQueFaltou? = null, item: Int? = null) =
        estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(texto), faltou = faltou, itemComErro = item) }

    // As chaves são únicas entre o formulário e a conferência do recibo: a mesma edição serve às duas listas.
    private fun mudarItem(chave: Int, mudar: (ItemDoFormulario) -> ItemDoFormulario) = estadoAtual.update { atual ->
        atual.copy(
            itens = atual.itens.map { if (it.chave == chave) mudar(it) else it },
            itensDoRecibo = atual.itensDoRecibo.map { if (it.chave == chave) mudar(it) else it },
            itemComErro = null,
        )
    }

    private fun parcelasDo(modo: ModoDaCompra, atual: EstadoDoFormulario) =
        if (modo == atual.modo) atual.parcelas else if (modo == ModoDaCompra.PARCELADO) "2" else "1"

    private companion object {
        const val CONTA_PADRAO = "conta_padrao"
        const val MOEDA_SEM_PREFERENCIA = "EUR"
        const val MAXIMO_DE_ITENS = 100
        val VALOR_MAXIMO = BigDecimal(1_000_000)
    }
}

/**
 * Em que fatura cai uma compra, com as regras do servidor (fatura.ts): antes do dia do fechamento,
 * na fatura do mês; do fechamento em diante, na do mês seguinte.
 *
 * @return o mês da fatura (AAAA-MM) e o vencimento dela (AAAA-MM-DD)
 */
internal fun faturaDaCompra(fechamento: Int, vencimento: Int, compra: LocalDate): Pair<String, String> {
    val mes = java.time.YearMonth.from(compra)
    val fechaNoMes = minOf(fechamento, mes.lengthOfMonth())
    val daFatura = if (compra.dayOfMonth < fechaNoMes) mes else mes.plusMonths(1)
    val doVencimento = if (vencimento > fechamento) daFatura else daFatura.plusMonths(1)
    return daFatura.toString() to doVencimento.atDay(minOf(vencimento, doVencimento.lengthOfMonth())).toString()
}
