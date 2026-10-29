package br.com.piggu.feature.receitas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.CacheDeRespostas
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.mesAtual
import br.com.piggu.core.model.AReceber
import br.com.piggu.core.model.ContaFixa
import br.com.piggu.core.model.MembroDaFamilia
import br.com.piggu.core.model.NovoAReceber
import br.com.piggu.core.model.Receita
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.network.mensagemDeErro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Autor das mudanças feitas pelo próprio Piggu (conta fixa automática, resgate). */
internal const val SISTEMA = "00000000-0000-0000-0000-000000000000"

/** O campo do "a receber" que barrou o envio, para marcar como inválido. */
enum class CampoDoAReceber { Quem, Valor }

/** O que espera o "Apagar" ou o "Parar de repetir" da pessoa (o confirm do site). */
sealed interface Confirmacao {
    data class ApagarReceita(val receita: Receita) : Confirmacao
    data class PararDeRepetir(val fixa: ContaFixa) : Confirmacao
    data class ApagarAReceber(val conta: AReceber) : Confirmacao
}

data class EstadoDeReceitas(
    /** aaaa-mm da lista. */
    val mes: String,
    val mesAtual: String,
    /** aaaa-mm-dd: o "a receber" de antes disso está atrasado. */
    val hoje: String,
    val receitas: Carga<List<Receita>> = Carga.Carregando,
    /** Quando foi guardado o que aparece, se a atualização falhou ("28 de out., 10:00"). */
    val guardadoEm: String? = null,
    val conectado: Boolean = true,
    val recorrentes: List<ContaFixa> = emptyList(),
    val aReceber: List<AReceber> = emptyList(),
    val membros: List<MembroDaFamilia> = emptyList(),
    val eu: String? = null,
    val moeda: String = "EUR",
    val aviso: Aviso? = null,
    val erro: MensagemDeErro? = null,
    val confirmacao: Confirmacao? = null,
    val quem: String = "",
    val motivo: String = "",
    val valorAReceber: BigDecimal? = null,
    val dataAReceber: String = hoje,
    val faltouNoAReceber: CampoDoAReceber? = null,
    val guardando: Boolean = false,
) {
    val total: BigDecimal
        get() = (receitas as? Carga.Pronto)?.valor.orEmpty().fold(BigDecimal.ZERO) { soma, receita -> soma + receita.valor }

    /** Mora mais alguém na casa: só assim aparece de quem é a receita. */
    val temCompanhia: Boolean get() = membros.size > 1

    /** A compartilhada de outra pessoa só ela apaga. */
    fun podeApagar(receita: Receita): Boolean = receita.compartilhado != true || receita.usuario == eu

    fun atrasada(conta: AReceber): Boolean = conta.data < hoje

    /** "de Beto" na receita de outra pessoa da casa (nomeNaFamilia de pessoas-da-familia.ts). */
    fun deQuem(receita: Receita): Aviso? = when {
        receita.usuario == eu || !temCompanhia -> null
        receita.usuario == SISTEMA -> Aviso(R.string.receitas_de_piggu)
        else -> membros.firstOrNull { it.id == receita.usuario }?.let { Aviso(R.string.receitas_de, listOf(it.nome)) }
            ?: Aviso(R.string.receitas_de_ex_membro)
    }
}

/**
 * A aba Receitas (features/incomes e ReceitasView do iOS): as receitas do mês, as que entram
 * todo mês e o dinheiro a receber. Recarrega quando muda o mês, quando algo é lançado
 * ([Lancamentos]) e quando a internet volta. Toda mudança avisa as outras abas.
 */
@HiltViewModel
class ReceitasViewModel internal constructor(
    private val financas: ServicoDeFinancas,
    private val cache: CacheDeRespostas,
    private val listas: ListasParaLancar,
    sessao: Sessao,
    private val lancamentos: Lancamentos,
    private val relogio: Clock,
    conectado: StateFlow<Boolean>,
) : ViewModel() {
    @Inject constructor(
        financas: ServicoDeFinancas,
        cache: CacheDeRespostas,
        listas: ListasParaLancar,
        sessao: Sessao,
        lancamentos: Lancamentos,
        relogio: Clock,
        rede: MonitorDeRede,
    ) : this(financas, cache, listas, sessao, lancamentos, relogio, rede.conectado)

    private val mes = MutableStateFlow(relogio.mesAtual())
    private val recarga = MutableStateFlow(0)
    private val estadoAtual = MutableStateFlow(
        EstadoDeReceitas(
            mes = mes.value,
            mesAtual = mes.value,
            hoje = LocalDate.now(relogio).toString(),
            eu = sessao.usuario?.id,
            moeda = sessao.usuario?.preferencias?.moeda ?: "EUR",
        ),
    )
    val estado: StateFlow<EstadoDeReceitas> = estadoAtual.asStateFlow()

    init {
        viewModelScope.launch {
            combine(mes, lancamentos.versao, recarga, conectado) { mes, _, _, conectado -> mes to conectado }
                .collectLatest { (mes, conectado) ->
                    estadoAtual.update { it.copy(conectado = conectado) }
                    carregar(mes)
                }
        }
    }

    fun mudarMes(novo: String) {
        mes.value = novo
        estadoAtual.update { it.copy(mes = novo) }
    }

    fun tentarDeNovo() = recarga.update { it + 1 }

    fun pedirParaApagar(receita: Receita) = estadoAtual.update { it.copy(confirmacao = Confirmacao.ApagarReceita(receita)) }
    fun pedirParaPararDeRepetir(fixa: ContaFixa) = estadoAtual.update { it.copy(confirmacao = Confirmacao.PararDeRepetir(fixa)) }
    fun pedirParaApagarAReceber(conta: AReceber) = estadoAtual.update { it.copy(confirmacao = Confirmacao.ApagarAReceber(conta)) }
    fun desistir() = estadoAtual.update { it.copy(confirmacao = null) }

    fun confirmar() {
        val confirmacao = estadoAtual.value.confirmacao ?: return
        estadoAtual.update { it.copy(confirmacao = null) }
        executar {
            when (confirmacao) {
                is Confirmacao.ApagarReceita -> financas.excluirReceita(confirmacao.receita.id)
                is Confirmacao.PararDeRepetir -> financas.excluirContaFixa(confirmacao.fixa.id)
                is Confirmacao.ApagarAReceber -> financas.excluirAReceber(confirmacao.conta.id)
            }
        }
    }

    /** "Recebi": vira receita de hoje, e a lista vai para o mês em que o dinheiro entrou. */
    fun receber(conta: AReceber) = executar(Aviso(R.string.a_receber_pagou, listOf(conta.quem))) {
        financas.receber(conta.id)
        mudarMes(estadoAtual.value.mesAtual)
    }

    fun mudarQuem(quem: String) = estadoAtual.update { it.copy(quem = quem.take(80)) }
    fun mudarMotivo(motivo: String) = estadoAtual.update { it.copy(motivo = motivo.take(200)) }
    fun mudarValorAReceber(valor: BigDecimal?) = estadoAtual.update { it.copy(valorAReceber = valor) }
    fun mudarDataAReceber(data: String) = estadoAtual.update { it.copy(dataAReceber = data) }

    fun guardarAReceber() {
        val e = estadoAtual.value
        if (e.guardando) return
        val quem = e.quem.trim()
        val valor = e.valorAReceber
        when {
            quem.isEmpty() -> recusar(CampoDoAReceber.Quem, R.string.a_receber_falta_quem)
            valor == null || valor.signum() <= 0 -> recusar(CampoDoAReceber.Valor, R.string.a_receber_falta_valor)
            e.dataAReceber.isBlank() -> recusar(null, R.string.a_receber_falta_data)
            else -> {
                estadoAtual.update { it.copy(guardando = true, faltouNoAReceber = null, erro = null) }
                viewModelScope.launch {
                    try {
                        financas.criarAReceber(NovoAReceber(quem, e.motivo.trim(), valor, e.dataAReceber))
                        estadoAtual.update {
                            it.copy(quem = "", motivo = "", valorAReceber = null, guardando = false, aviso = Aviso(R.string.a_receber_guardado))
                        }
                        lancamentos.avisar()
                    } catch (falha: ErroDaApi) {
                        estadoAtual.update { it.copy(guardando = false, erro = mensagemDeErro(falha), aviso = null) }
                    }
                }
            }
        }
    }

    private fun recusar(campo: CampoDoAReceber?, texto: Int) =
        estadoAtual.update { it.copy(faltouNoAReceber = campo, erro = MensagemDeErro.DoApp(texto), aviso = null) }

    /** Deu certo: tira o erro, mostra o [aviso] e avisa as outras abas (que esta também ouve). */
    private fun executar(aviso: Aviso? = null, acao: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                acao()
                estadoAtual.update { it.copy(erro = null, aviso = aviso ?: it.aviso) }
                lancamentos.avisar()
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha), aviso = null) }
            }
        }
    }

    /** A lista é a que manda nos estados; as outras partes, sem rede, ficam com o guardado ou vazias, como no site. */
    private suspend fun carregar(mes: String) = coroutineScope {
        launch {
            cache.carregar("receitas/$mes", ListSerializer(Receita.serializer())) { financas.listarReceitas(mes) }.collect { carga ->
                val guardadaEm = (carga as? Carga.Pronto)?.guardadaEm
                estadoAtual.update { it.copy(receitas = carga, guardadoEm = guardadaEm?.let { quando -> quandoGuardado(quando, relogio.zone) }) }
            }
        }
        launch {
            cache.carregar("receitas-recorrentes/$mes", ListSerializer(ContaFixa.serializer())) { financas.receitasRecorrentes(mes) }
                .collect { carga ->
                    when (carga) {
                        is Carga.Pronto -> estadoAtual.update { it.copy(recorrentes = carga.valor) }
                        is Carga.Falhou -> estadoAtual.update { it.copy(recorrentes = emptyList()) }
                        Carga.Carregando -> Unit
                    }
                }
        }
        launch {
            cache.carregar("a-receber", ListSerializer(AReceber.serializer())) { financas.aReceber() }.collect { carga ->
                if (carga is Carga.Pronto) estadoAtual.update { it.copy(aReceber = carga.valor) }
            }
        }
        launch {
            listas.familia().collect { carga ->
                if (carga is Carga.Pronto) estadoAtual.update { it.copy(membros = carga.valor.membros) }
            }
        }
    }
}
