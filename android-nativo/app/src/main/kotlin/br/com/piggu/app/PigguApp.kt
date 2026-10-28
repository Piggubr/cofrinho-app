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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import br.com.piggu.core.data.Pendencia
import br.com.piggu.core.designsystem.ModoDeCor
import br.com.piggu.core.designsystem.PigguTheme
import br.com.piggu.core.ui.AbaEmBreve
import br.com.piggu.core.ui.CasoDeErro
import br.com.piggu.core.ui.HumorDoPorquinho
import br.com.piggu.core.ui.Porquinho
import br.com.piggu.core.ui.TelaDeErro
import br.com.piggu.core.ui.lembrarAbrirNoSite
import br.com.piggu.feature.gastos.GastosScreen
import br.com.piggu.feature.login.TelaDeLogin
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
            Raiz.Login -> {
                LinksSemSessao(links, abrirNoSite)
                TelaDeLogin(
                    esqueciASenha = { abrirNoSite(Tela.EsqueciSenha.caminho) },
                    criarConta = { abrirNoSite(Tela.Entrar.caminho) },
                )
            }
            is Raiz.Pendente -> TelaDePendencia(raiz.pendencia, abrirNoSite, viewModel::conferirPendencia)
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

/** Sem sessão, os links das telas abertas (termos, senha, e-mail) abrem no site; os outros esperam o login. */
@Composable
private fun LinksSemSessao(links: Flow<Destino>, abrirNoSite: (String) -> Unit) {
    LaunchedEffect(links) {
        links.collect { destino ->
            val tela = (destino as? Destino.Abrir)?.tela ?: return@collect
            if (tela in Tela.abertas || tela is Tela.ConfirmarEmail || tela is Tela.RedefinirSenha) abrirNoSite(tela.caminho)
        }
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
) {
    val pilha = rememberNavBackStack(Tela.Painel)

    fun abrir(tela: Tela) {
        when {
            // perfilGuard('ADMIN', 'TITULAR'): o Premium é do titular; os outros vão ao Painel.
            tela == Tela.Plano && !perfil.ehTitular -> pilha.irPara(Tela.Painel)
            tela in Tela.abas -> pilha.irPara(tela)
            tela == Tela.Erro || tela == Tela.NaoEncontrada -> pilha.add(tela)
            tela.nativa -> pilha.irPara(Tela.Painel)
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

    val topo = pilha.lastOrNull()
    if (topo == Tela.Erro || topo == Tela.NaoEncontrada) {
        BackHandler { pilha.removeLastOrNull() }
        TelaDeErro(
            if (topo == Tela.Erro) CasoDeErro.Servidor else CasoDeErro.NaoEncontrada,
            irParaOInicio = { pilha.irPara(Tela.Painel) },
            voltarOuTentar = { pilha.removeLastOrNull() },
        )
        return
    }

    Moldura(
        perfil = perfil,
        abaAtual = topo as? Tela,
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
                    TelaDoMais(perfil, BuildConfig.VERSION_NAME, ::abrir, sair, PaddingValues(bottom = FOLGA_DO_LANCAR))
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

/** Os Termos mudaram ou falta o cadastro (termosGuard): o aceite e o "Sobre você" chegam nativos na A3. */
@Composable
private fun TelaDePendencia(pendencia: Pendencia, abrirNoSite: (String) -> Unit, conferir: () -> Unit) {
    val (titulo, texto, tela) = when (pendencia) {
        Pendencia.Termos -> Triple(R.string.pendente_termos_titulo, R.string.pendente_termos_texto, Tela.Aceite)
        Pendencia.SobreVoce -> Triple(R.string.pendente_cadastro_titulo, R.string.pendente_cadastro_texto, Tela.SobreVoce)
    }
    Aviso(stringResource(titulo), stringResource(texto)) {
        Button({ abrirNoSite(tela.caminho) }) { Text(stringResource(R.string.abrir_no_site)) }
        OutlinedButton(conferir) { Text(stringResource(R.string.pendente_ja_fiz)) }
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
