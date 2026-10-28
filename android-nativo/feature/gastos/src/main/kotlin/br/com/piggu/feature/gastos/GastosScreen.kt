package br.com.piggu.feature.gastos

import androidx.compose.runtime.Composable
import br.com.piggu.feature.gastos.lista.TelaDeGastos

/**
 * A aba enquanto o `app` não chama a [TelaDeGastos] com o link e o lançar.
 * shortcut: só para o `app` compilar; a integração da A4 troca a chamada e apaga este arquivo.
 */
@Composable
fun GastosScreen() = TelaDeGastos(gastoParaAbrir = null, aoAbrirGasto = {}, lancarGasto = {})
