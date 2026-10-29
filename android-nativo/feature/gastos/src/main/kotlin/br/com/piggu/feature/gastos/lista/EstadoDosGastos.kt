package br.com.piggu.feature.gastos.lista

import androidx.annotation.StringRes
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.GastoAguardando
import br.com.piggu.core.model.AcertoDeDivisao
import br.com.piggu.core.model.ContaOuCartao
import br.com.piggu.core.model.Gasto
import br.com.piggu.core.model.LinhaDoExtrato
import br.com.piggu.core.model.MembroDaFamilia
import br.com.piggu.core.model.RegraDeCategoria
import br.com.piggu.core.model.TipoDeConta
import br.com.piggu.core.model.TransferenciaDoAcerto
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.feature.gastos.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer

/** Forma de pagamento pelo tipo da conta do gasto (FiltroDePagamento do iOS). */
enum class FiltroDePagamento(@param:StringRes val nome: Int) {
    Todas(R.string.gastos_pagamento_todas),
    Cartao(R.string.gastos_pagamento_cartao),
    Debito(R.string.gastos_pagamento_debito),
    SemConta(R.string.gastos_pagamento_sem_conta),
}

/** Um texto do app com os argumentos dele (os avisos de sucesso). */
data class Mensagem(@param:StringRes val texto: Int, val argumentos: List<Any> = emptyList())

/** Um gasto da lista; [aguardando] é um da fila sem internet, que ainda não subiu. */
data class LinhaDeGasto(val gasto: Gasto, val aguardando: Boolean = false)

/** Os gastos de um dia (aaaa-mm-dd), na ordem do servidor. */
data class DiaDeGastos(val dia: String, val gastos: List<LinhaDeGasto>)

data class EdicaoDoGasto(
    val gasto: Gasto,
    val item: String,
    val categoria: String,
    val valor: BigDecimal?,
    val compartilhado: Boolean,
    /** Só quem lançou muda o compartilhado, e só com mais gente na casa. */
    val podeCompartilhar: Boolean,
    val salvando: Boolean = false,
    val erro: MensagemDeErro? = null,
)

/** Apagado na tela, esperando o "Desfazer" da Snackbar acabar para ir ao servidor. */
data class ExclusaoPendente(val gasto: Gasto, val eAsProximas: Boolean)

data class RegraSugerida(val termo: String, val categoria: String)

data class LinhaDaPrevia(val linha: LinhaDoExtrato, val marcada: Boolean)

/** O CSV do mês, pronto para o compartilhamento do Android. */
class CsvDoMes(val nome: String, val bytes: ByteArray)

data class EstadoDosGastos(
    /** aaaa-mm */
    val mes: String,
    val mesAtual: String,
    val moeda: String? = null,
    /** Id da pessoa logada. */
    val eu: String? = null,
    val carga: Carga<List<Gasto>> = Carga.Carregando,
    val conectado: Boolean = true,
    val busca: String = "",
    val filtroDeCategoria: String? = null,
    val filtroDePagamento: FiltroDePagamento = FiltroDePagamento.Todas,
    val categorias: List<String> = emptyList(),
    val contas: List<ContaOuCartao> = emptyList(),
    val membros: List<MembroDaFamilia> = emptyList(),
    val aguardando: List<GastoAguardando> = emptyList(),
    val recusados: Int = 0,
    /** Apagados nesta tela: somem na hora, antes da lista voltar do servidor. */
    val ocultos: Set<String> = emptySet(),
    /** O gasto aberto pelo link, marcado na lista. */
    val destacado: String? = null,
    val acerto: List<AcertoDeDivisao> = emptyList(),
    val transferencias: List<TransferenciaDoAcerto> = emptyList(),
    val regras: List<RegraDeCategoria> = emptyList(),
    val termoDaRegra: String = "",
    val categoriaDaRegra: String = "",
    val sugestao: RegraSugerida? = null,
    val edicao: EdicaoDoGasto? = null,
    /** Parcela com as próximas: pergunta se apaga só esta. */
    val confirmando: Gasto? = null,
    val exclusao: ExclusaoPendente? = null,
    val previa: List<LinhaDaPrevia>? = null,
    val contaDoExtrato: String = "",
    val ocupado: Boolean = false,
    val csv: CsvDoMes? = null,
    val aviso: Mensagem? = null,
    val erro: MensagemDeErro? = null,
) {
    val filtrando: Boolean
        get() = busca.isNotBlank() || filtroDeCategoria != null || filtroDePagamento != FiltroDePagamento.Todas

    /** Os do servidor, sem os apagados agora; nulo enquanto carrega ou se falhou. */
    val gastos: List<Gasto>?
        get() = (carga as? Carga.Pronto)?.valor?.filter { it.id !in ocultos }

    /** Os da fila deste mês primeiro, depois os do servidor, já filtrados e agrupados por dia. */
    val dias: List<DiaDeGastos>
        get() = (aguardandoDoMes() + gastos.orEmpty().map { LinhaDeGasto(it) })
            .filter { passaNosFiltros(it.gasto) }
            .groupBy { it.gasto.data }
            .toSortedMap(reverseOrder())
            .map { (dia, linhas) -> DiaDeGastos(dia, linhas) }

    /** O que o servidor já tem, como no Painel: a fila entra quando subir. */
    val total: BigDecimal
        get() = dias.flatMap { it.gastos }.filterNot { it.aguardando }.fold(BigDecimal.ZERO) { soma, l -> soma + l.gasto.valor }

    val vazio: Boolean get() = gastos.isNullOrEmpty() && aguardandoDoMes().isEmpty()

    fun ehMeu(gasto: Gasto): Boolean = gasto.usuario != null && gasto.usuario == eu

    /** Do compartilhado, só quem lançou apaga (o site esconde a lixeira). */
    fun podeApagar(gasto: Gasto): Boolean = gasto.compartilhado != true || ehMeu(gasto)

    /** Mora mais alguém: mostra de quem é o gasto e o compartilhado. */
    val comCompanhia: Boolean get() = membros.size > 1

    fun passaNosFiltros(gasto: Gasto): Boolean {
        val termo = semAcento(busca.trim())
        if (termo.isNotEmpty() && termo !in semAcento("${gasto.item} ${gasto.estabelecimento.orEmpty()} ${gasto.categoria}")) return false
        if (filtroDeCategoria != null && gasto.categoria != filtroDeCategoria) return false
        val tipo = contas.firstOrNull { it.id == gasto.contaId }?.tipo
        return when (filtroDePagamento) {
            FiltroDePagamento.Todas -> true
            FiltroDePagamento.Cartao -> tipo == TipoDeConta.CARTAO
            FiltroDePagamento.Debito -> tipo == TipoDeConta.CONTA
            FiltroDePagamento.SemConta -> tipo == null
        }
    }

    /** Cada item da fila vira uma linha; o parcelado mostra a primeira parcela, como o servidor vai gravar. */
    private fun aguardandoDoMes(): List<LinhaDeGasto> = aguardando
        .filter { it.lancamento.data.startsWith(mes) }
        .flatMap { guardado ->
            val l = guardado.lancamento
            val parcelas = l.parcelas?.takeIf { it > 1 }
            l.itens.mapIndexed { i, item ->
                LinhaDeGasto(
                    Gasto(
                        id = "fila:${guardado.idDoEnvio}:$i",
                        data = l.data,
                        estabelecimento = l.estabelecimento,
                        item = item.item,
                        categoria = item.categoria.orEmpty(),
                        valor = parcelas?.let { item.valor.divide(BigDecimal(it), 2, RoundingMode.DOWN) } ?: item.valor,
                        origem = l.origem,
                        usuario = eu,
                        contaId = l.contaId,
                        parcela = parcelas?.let { 1 },
                        parcelas = parcelas,
                        moedaOriginal = item.moedaOriginal,
                        valorOriginal = item.valorOriginal,
                        compartilhado = l.compartilhado,
                    ),
                    aguardando = true,
                )
            }
        }
}

/** Quantas parcelas vêm depois desta. */
fun Gasto.seguintes(): Int = (parcelas ?: 0) - (parcela ?: parcelas ?: 0)

/** Autor das mudanças feitas pelo próprio Piggu (pessoas-da-familia.ts). */
const val SISTEMA = "00000000-0000-0000-0000-000000000000"

private fun semAcento(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
