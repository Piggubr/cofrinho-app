package br.com.piggu.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import br.com.piggu.app.moldura.FOLGA_DO_LANCAR
import br.com.piggu.app.moldura.Moldura
import br.com.piggu.app.moldura.PerfilDaMoldura
import br.com.piggu.app.moldura.TelaDoMais
import br.com.piggu.app.navegacao.Destino
import br.com.piggu.app.navegacao.Tela
import br.com.piggu.core.designsystem.ModoDeCor
import br.com.piggu.core.designsystem.PigguTheme
import br.com.piggu.core.ui.AbaEmBreve
import br.com.piggu.core.ui.HumorDoPorquinho
import br.com.piggu.core.ui.Porquinho
import br.com.piggu.core.ui.lembrarAbrirNoSite
import br.com.piggu.feature.gastos.GastosScreen
import br.com.piggu.feature.login.AvisoDeConfirmarEmail
import br.com.piggu.feature.login.DialogoDeExclusao
import br.com.piggu.feature.login.ExclusaoViewModel
import br.com.piggu.feature.painel.PainelScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Troca de aba deixando o Painel na base: o voltar de qualquer aba leva ao Painel e, de lá, sai do app. */
fun MutableList<NavKey>.irPara(aba: NavKey) {
    if (lastOrNull() == aba) return
    retainAll(listOf(Tela.Painel))
    if (aba != Tela.Painel) add(aba)
}

/** O app inteiro: o tema da pessoa e, pela sessão, o login, uma pendência ou a moldura. */
@Composable
fun PigguApp(links: Flow<Destino> = emptyFlow(), viewModel: AppViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val escuro = when (estado.modo) {
        ModoDeCor.Automatico -> isSystemInDarkTheme()
        ModoDeCor.Claro -> false
        ModoDeCor.Escuro -> true
    }
    BarrasDoSistema(escuro)
    PigguTheme(estado.cor, estado.modo, estado.coresDoCelular) {
        val abrirNoSite = lembrarAbrirNoSite()
        when (val raiz = estado.raiz) {
            Raiz.Abrindo -> Abrindo()
            Raiz.Atualizar -> AtualizeOApp()
            Raiz.Login -> EntradaSemSessao(links)
            is Raiz.Pendente -> key(raiz.pendencia) { PendenciaDaConta(raiz.pendencia, links, viewModel::sair) }
            is Raiz.Logado -> {
                val avisos = remember { SnackbarHostState() }
                val corPadrao = stringResource(R.string.cor_voltou_ao_padrao)
                LaunchedEffect(estado.avisarCorPadrao) {
                    if (estado.avisarCorPadrao) {
                        viewModel.avisouCorPadrao()
                        avisos.showSnackbar(corPadrao)
                    }
                }
                AppLogado(raiz.perfil, links, avisos, abrirNoSite, viewModel::sair)
            }
        }
    }
}

/** Com o modo fixo no Perfil, a barra de status e a de navegação seguem o app, não o aparelho. */
@Composable
private fun BarrasDoSistema(escuro: Boolean) {
    val atividade = LocalActivity.current as? ComponentActivity ?: return
    LaunchedEffect(escuro) {
        val estilo = if (escuro) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        atividade.enableEdgeToEdge(estilo, estilo)
    }
}

/**
 * Logado: a moldura com as abas. A pilha guarda as abas e as telas de erro (sem moldura); o que
 * ainda não é nativo abre no site.
 */
@Composable
fun AppLogado(
    perfil: PerfilDaMoldura,
    links: Flow<Destino>,
    avisos: SnackbarHostState,
    abrirNoSite: (String) -> Unit,
    sair: () -> Unit,
    // Excluir a conta e reenviar a confirmação têm ViewModel do Hilt; as capturas desligam.
    acoesDaConta: Boolean = true,
) {
    val pilha = rememberNavBackStack(Tela.Painel)

    fun abrir(tela: Tela) {
        when {
            // perfilGuard('ADMIN', 'TITULAR'): o Premium é do titular; os outros vão ao Painel.
            tela == Tela.Plano && !perfil.ehTitular -> pilha.irPara(Tela.Painel)
            tela in Tela.abas -> pilha.irPara(tela)
            // Já logado, o login e as pendências não têm o que fazer: fica no Painel.
            tela == Tela.Entrar || tela == Tela.Aceite || tela == Tela.SobreVoce -> pilha.irPara(Tela.Painel)
            tela.nativa -> pilha.add(tela)
            else -> abrirNoSite(tela.caminho)
        }
    }

    LaunchedEffect(links) {
        links.collect { destino ->
            when (destino) {
                is Destino.Abrir -> abrir(destino.tela)
                // O lançamento e o gasto chegam nativos na A4; até lá, o lançar abre no site.
                is Destino.LancarGasto -> abrirNoSite(caminhoDoLancar(destino))
                is Destino.VerGasto -> pilha.irPara(Tela.Gastos)
            }
        }
    }

    val topo = pilha.lastOrNull() as? Tela ?: Tela.Painel
    if (topo !in Tela.abas) {
        // Fora das abas (textos legais, CPF, links do e-mail, erros): tela cheia, sem a moldura.
        BackHandler { pilha.removeLastOrNull() }
        TelaCheia(topo, pilha, Tela.Painel, sair)
        return
    }

    Moldura(
        perfil = perfil,
        abaAtual = topo,
        demo = BuildConfig.FLAVOR == "demo",
        abrir = ::abrir,
        lancarGasto = { abrirNoSite(caminhoDoLancar(Destino.LancarGasto(null))) },
        lancarReceita = { abrirNoSite(Tela.Receitas.caminho) },
        sair = sair,
        avisos = avisos,
    ) {
        NavDisplay(
            backStack = pilha,
            onBack = { pilha.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<Tela.Painel> { PainelScreen() }
                entry<Tela.Gastos> { GastosScreen() }
                entry<Tela.Receitas> { AbaEmBreve(stringResource(R.string.tela_receitas), Tela.Receitas.caminho) }
                entry<Tela.Relatorios> { AbaEmBreve(stringResource(R.string.tela_relatorios), Tela.Relatorios.caminho) }
                entry<Tela.Mais> {
                    val exclusao: ExclusaoViewModel? = if (acoesDaConta) hiltViewModel() else null
                    TelaDoMais(
                        perfil,
                        BuildConfig.VERSION_NAME,
                        ::abrir,
                        sair,
                        PaddingValues(bottom = FOLGA_DO_LANCAR),
                        excluirConta = { exclusao?.abrir() },
                        avisoDoEmail = { if (acoesDaConta) AvisoDeConfirmarEmail(perfil.email) },
                    )
                    exclusao?.let { DialogoDeExclusao(it) }
                }
            },
        )
    }
}

/** O lançamento rápido do site (/gastos?rapido=1&valor=12.50), o mesmo link do app antigo. */
private fun caminhoDoLancar(destino: Destino.LancarGasto) =
    "/gastos?rapido=1" + (destino.valor?.let { "&valor=${it.toPlainString()}" } ?: "")

/** Abrindo sem perfil guardado: o porquinho enquanto o servidor responde. */
@Composable
private fun Abrindo() {
    Surface(Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Porquinho(HumorDoPorquinho.Feliz)
        }
    }
}

/** A API respondeu 426 com ATUALIZE_O_APP: esta versão não serve mais. */
@Composable
private fun AtualizeOApp() {
    val contexto = LocalContext.current
    Aviso(stringResource(R.string.atualize_titulo), stringResource(R.string.atualize_texto)) {
        Button({ abrirAFichaDoPlay(contexto) }) { Text(stringResource(R.string.atualize_botao)) }
    }
}

@Composable
private fun Aviso(titulo: String, texto: String, botoes: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Porquinho(HumorDoPorquinho.Perdido, largura = 160.dp)
            Text(titulo, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                texto,
                Modifier.widthIn(max = 420.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            botoes()
        }
    }
}

/** A ficha do app na Play Store; sem o app da loja, a página dela no navegador. */
private fun abrirAFichaDoPlay(contexto: Context) {
    val pacote = contexto.packageName.removeSuffix(".demo")
    try {
        contexto.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$pacote".toUri()))
    } catch (_: ActivityNotFoundException) {
        contexto.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$pacote".toUri()))
    }
}
