package br.com.piggu.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import br.com.piggu.app.navegacao.Destino
import br.com.piggu.app.navegacao.Tela
import br.com.piggu.app.navegacao.lerLink
import br.com.piggu.core.data.Pendencia
import br.com.piggu.core.ui.CasoDeErro
import br.com.piggu.core.ui.TelaDeErro
import br.com.piggu.core.ui.lembrarAbrirNoSite
import br.com.piggu.feature.calendario.TelaDoCalendario
import br.com.piggu.feature.contas.TelaDasContasECartoes
import br.com.piggu.feature.contasfixas.TelaDasContasDoMes
import br.com.piggu.feature.orcamentos.TelaDoOrcamento
import br.com.piggu.core.ui.formatos.lerValor
import br.com.piggu.feature.legal.DocumentoLegal
import br.com.piggu.feature.legal.TelaDeTextoLegal
import br.com.piggu.feature.login.TelaDeConfirmarEmail
import br.com.piggu.feature.login.TelaDeEsqueciASenha
import br.com.piggu.feature.login.TelaDeLogin
import br.com.piggu.feature.login.TelaDeSenhaNova
import br.com.piggu.feature.login.TelaDoAceite
import br.com.piggu.feature.login.cadastro.FormularioDeCadastro
import br.com.piggu.feature.login.cpf.TelaDoCpf
import br.com.piggu.feature.login.sobrevoce.TelaSobreVoce
import kotlinx.coroutines.flow.Flow

/** Volta a pilha só para [base], como depois de entrar ou de trocar a senha. */
private fun MutableList<NavKey>.recomecar(base: Tela) {
    clear()
    add(base)
}

private fun MutableList<NavKey>.voltar(base: Tela) {
    if (size > 1) removeAt(lastIndex) else recomecar(base)
}

/**
 * As telas cheias, fora da moldura: a entrada (A3), os textos legais e as de erro. Usada sem
 * sessão, com uma pendência e por cima das abas.
 *
 * @param base a tela de baixo da pilha, para onde "voltar" e "entrar" levam.
 */
@Composable
fun TelaCheia(tela: Tela, pilha: MutableList<NavKey>, base: Tela, sair: () -> Unit) {
    fun abrir(destino: Tela) { pilha.add(destino) }
    val abrirNoSite = lembrarAbrirNoSite()
    val voltar = { pilha.voltar(base) }
    when (tela) {
        Tela.Entrar -> TelaDeLogin(
            esqueciASenha = { abrir(Tela.EsqueciSenha) },
            abrirTermos = { abrir(Tela.Termos) },
            abrirPrivacidade = { abrir(Tela.Privacidade) },
            cadastro = { aoCriar ->
                FormularioDeCadastro(aoCriar = aoCriar, abrirTermos = { abrir(Tela.Termos) }, abrirPrivacidade = { abrir(Tela.Privacidade) })
            },
        )
        Tela.EsqueciSenha -> TelaDeEsqueciASenha(voltarParaEntrar = voltar)
        is Tela.RedefinirSenha -> TelaDeSenhaNova(
            tela.token,
            entrar = { pilha.recomecar(base) },
            pedirOutroLink = { pilha.recomecar(base); abrir(Tela.EsqueciSenha) },
        )
        is Tela.ConfirmarEmail -> TelaDeConfirmarEmail(tela.token, abrirOPiggu = { pilha.recomecar(base) })
        Tela.Termos, Tela.Privacidade -> TelaDeTextoLegal(
            if (tela == Tela.Termos) DocumentoLegal.Termos else DocumentoLegal.Privacidade,
            voltar = voltar,
            abrirLinkInterno = { caminho ->
                // Um texto aponta para o outro (/privacidade nos Termos); o resto é do site.
                val destino = (lerLink(caminho, ::lerValor) as? Destino.Abrir)?.tela
                if (destino == Tela.Termos || destino == Tela.Privacidade) abrir(destino)
            },
        )
        Tela.Aceite -> TelaDoAceite(abrirTermos = { abrir(Tela.Termos) }, abrirPrivacidade = { abrir(Tela.Privacidade) })
        Tela.SobreVoce -> TelaSobreVoce(sair = sair)
        Tela.Cpf -> TelaDoCpf(voltar = voltar)
        // Organização (A5), abertas pelo Mais e pela gaveta.
        Tela.ContasFixas -> TelaDasContasDoMes(voltar = voltar)
        Tela.Orcamentos -> TelaDoOrcamento(voltar = voltar, abrirPremium = { abrirNoSite(Tela.Plano.caminho) })
        Tela.Calendario -> TelaDoCalendario(voltar = voltar)
        Tela.ContasECartoes -> TelaDasContasECartoes(voltar = voltar, abrirNoSite = abrirNoSite)
        Tela.Erro -> TelaDeErro(CasoDeErro.Servidor, irParaOInicio = { pilha.recomecar(base) }, voltarOuTentar = voltar)
        else -> TelaDeErro(CasoDeErro.NaoEncontrada, irParaOInicio = { pilha.recomecar(base) }, voltarOuTentar = voltar)
    }
}

/** Uma pilha só de telas cheias, com a navegação de voltar do sistema. */
@Composable
private fun PilhaDeTelasCheias(base: Tela, links: Flow<Destino>, aceita: (Tela) -> Boolean, sair: () -> Unit) {
    val pilha = rememberNavBackStack(base)
    LaunchedEffect(links) {
        links.collect { destino ->
            val tela = (destino as? Destino.Abrir)?.tela ?: return@collect
            if (aceita(tela) && pilha.lastOrNull() != tela) pilha.add(tela)
        }
    }
    NavDisplay(
        backStack = pilha,
        onBack = { pilha.voltar(base) },
        entryProvider = { chave -> NavEntry(chave) { TelaCheia(chave as Tela, pilha, base, sair) } },
    )
}

/** Sem sessão: o login e as telas abertas (os links do e-mail e os textos legais). */
@Composable
fun EntradaSemSessao(links: Flow<Destino>) {
    PilhaDeTelasCheias(Tela.Entrar, links, aceita = Tela::aberta, sair = {})
}

/** O que falta antes do resto (termosGuard): o aceite, ou o "Sobre você"; os textos legais abrem por cima. */
@Composable
fun PendenciaDaConta(pendencia: Pendencia, links: Flow<Destino>, sair: () -> Unit) {
    val base = if (pendencia == Pendencia.Termos) Tela.Aceite else Tela.SobreVoce
    PilhaDeTelasCheias(base, links, aceita = { it == Tela.Termos || it == Tela.Privacidade }, sair = sair)
}
