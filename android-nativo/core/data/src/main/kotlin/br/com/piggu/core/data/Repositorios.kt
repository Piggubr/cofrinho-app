package br.com.piggu.core.data

import br.com.piggu.core.model.Categorias
import br.com.piggu.core.model.ContaOuCartao
import br.com.piggu.core.model.Familia
import br.com.piggu.core.model.Gasto
import br.com.piggu.core.model.ResumoDoMes
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.network.ServicoDeIdentidade
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** O mês atual (aaaa-mm) no fuso do relógio do app. */
fun Clock.mesAtual(): String = YearMonth.now(this).toString()

/** O resumo e os gastos do mês, guardados para abrir sem internet ("resumo/2026-10", "gastos/2026-10"). */
@Singleton
class RepositorioDoMes @Inject constructor(
    private val cache: CacheDeRespostas,
    private val financas: ServicoDeFinancas,
) {
    fun resumo(mes: String): Flow<Carga<ResumoDoMes>> =
        cache.carregar("resumo/$mes", ResumoDoMes.serializer()) { financas.resumoDoMes(mes) }

    fun gastos(mes: String): Flow<Carga<List<Gasto>>> =
        cache.carregar("gastos/$mes", ListSerializer(Gasto.serializer())) { financas.listarGastos(mes) }
}

/**
 * As listas usadas para lançar sem sinal (listas-guardadas.ts e o ngsw-config do site):
 * categorias, contas e cartões, e as pessoas da família.
 */
@Singleton
class ListasParaLancar @Inject constructor(
    private val cache: CacheDeRespostas,
    private val financas: ServicoDeFinancas,
    private val identidade: ServicoDeIdentidade,
) {
    fun categorias(): Flow<Carga<Categorias>> =
        cache.carregar("categorias", Categorias.serializer()) { financas.listarCategorias() }

    fun contas(): Flow<Carga<List<ContaOuCartao>>> =
        cache.carregar("contas", ListSerializer(ContaOuCartao.serializer())) { financas.contas() }

    fun familia(): Flow<Carga<Familia>> =
        cache.carregar("pessoas-da-familia", Familia.serializer()) { identidade.verFamilia() }
}
