package br.com.piggu.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aviso de que algo foi lançado fora da tela aberta (pelo +, pela fila sem internet) (lancamentos.ts).
 * As telas que mostram gastos ou receitas observam [versao] e recarregam quando ela muda.
 */
@Singleton
class Lancamentos @Inject constructor() {
    private val contador = MutableStateFlow(0)
    val versao: StateFlow<Int> = contador.asStateFlow()

    fun avisar() = contador.update { it + 1 }
}
