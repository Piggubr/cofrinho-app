package br.com.piggu.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import br.com.piggu.core.data.EscopoDoApp
import br.com.piggu.core.data.EstadoDaSessao
import br.com.piggu.core.data.Lancamentos
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.ehPremium
import br.com.piggu.core.data.mesAtual
import br.com.piggu.core.data.pendencia
import br.com.piggu.core.datastore.PreferenciasDoAparelho
import br.com.piggu.core.designsystem.VarianteDeCor
import br.com.piggu.core.model.MetaDoMes
import br.com.piggu.core.model.Usuario
import br.com.piggu.core.network.ServicoDeEstiloDeVida
import br.com.piggu.core.network.ServicoDeFinancas
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Grava o resumo dos widgets (AtualizadorDoSistema.swift do iOS): ao abrir o app, ao voltar para
 * a frente e depois de lançar um gasto ([Lancamentos]); ao sair da conta, apaga. O app chama
 * [vigiar] uma vez, no `PigguApplication.onCreate`.
 */
@Singleton
class AtualizadorDoWidget @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val sessao: Sessao,
    private val financas: ServicoDeFinancas,
    private val estiloDeVida: ServicoDeEstiloDeVida,
    private val preferencias: PreferenciasDoAparelho,
    private val lancamentos: Lancamentos,
    private val guarda: GuardaDoResumo,
    private val relogio: Clock,
    @EscopoDoApp private val escopo: CoroutineScope,
) {
    /** @param voltas emite a cada vez que o app vem para a frente; os testes passam um fluxo fixo. */
    fun vigiar(voltas: Flow<Unit> = voltasParaAFrente()) {
        escopo.launch {
            combine(sessao.estado, lancamentos.versao, voltas) { estado, _, _ -> estado }
                .collectLatest { estado ->
                    when {
                        estado is EstadoDaSessao.Deslogado -> {
                            guarda.apagar()
                            redesenhar()
                        }
                        estado is EstadoDaSessao.Logado && estado.usuario.pendencia == null -> atualizar(estado.usuario)
                    }
                }
        }
    }

    private suspend fun atualizar(usuario: Usuario) {
        val mes = relogio.mesAtual()
        // Sem rede ou com erro, o widget fica com o último resumo.
        val resumo = runCatching {
            coroutineScope {
                val gastos = async { financas.listarGastos(mes) }
                val limites = async { runCatching { financas.listarMetas() }.getOrNull() }
                val metas = async { runCatching { estiloDeVida.listarMetasDoMes() }.getOrNull().orEmpty() }
                val cor = if (preferencias.coresDoCelular.first()) null
                else VarianteDeCor.deId(preferencias.cor.first()).efetiva(usuario.ehPremium).id
                resumoDoMes(
                    mes = mes,
                    gasto = gastos.await().fold(BigDecimal.ZERO) { soma, gasto -> soma + gasto.valor },
                    limite = limites.await()?.get(mes),
                    metas = metas.await(),
                    moeda = usuario.preferencias?.moeda,
                    cor = cor,
                    agora = relogio.millis(),
                )
            }
        }.getOrNull() ?: return
        guarda.gravar(resumo)
        redesenhar()
    }

    private suspend fun redesenhar() {
        GastoDoMesWidget().updateAll(contexto)
        MetaDoMesWidget().updateAll(contexto)
        // Este só leva a cor.
        LancarGastoWidget().updateAll(contexto)
    }
}

/** O resumo a partir das respostas da API (resumo(mes:gastos:limite:metas:) do iOS). */
internal fun resumoDoMes(
    mes: String,
    gasto: BigDecimal,
    limite: BigDecimal?,
    metas: List<MetaDoMes>,
    moeda: String?,
    cor: String?,
    agora: Long,
): ResumoDoWidget {
    val meta = metas.firstOrNull { !it.concluida } ?: metas.lastOrNull()
    return ResumoDoWidget(
        mes = mes,
        gasto = gasto,
        limite = limite?.takeIf { it.signum() > 0 },
        moeda = moeda,
        meta = meta?.let { ResumoDoWidget.Meta(it.titulo, it.atual, it.alvo) },
        metasConcluidas = metas.count { it.concluida },
        metasTotal = metas.size,
        cor = cor,
        atualizadoEm = agora,
    )
}

/** Só quando entra na frente: ir para trás não cancela o resumo que está sendo gravado. */
private fun voltasParaAFrente(): Flow<Unit> =
    ProcessLifecycleOwner.get().lifecycle.currentStateFlow
        .map { it.isAtLeast(Lifecycle.State.STARTED) }
        .distinctUntilChanged()
        .filter { it }
        .map { }
