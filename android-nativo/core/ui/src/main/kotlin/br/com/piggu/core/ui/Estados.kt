package br.com.piggu.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.designsystem.R as Icones

/**
 * Carregando: o formato do que vem, em blocos apagados, para a tela não pular quando os dados
 * chegam (app-carregando do site, EsqueletoDeLista do iOS). O TalkBack lê só "Carregando…".
 */
@Composable
fun Esqueleto(modifier: Modifier = Modifier, linhas: Int = 6) {
    val carregando = stringResource(R.string.carregando)
    val bloco = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier.padding(16.dp).clearAndSetSemantics { contentDescription = carregando },
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        repeat(linhas) { indice ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth(if (indice % 2 == 0) 0.6f else 0.4f).height(14.dp).background(bloco, RoundedCornerShape(6.dp)))
                    Box(Modifier.fillMaxWidth(0.35f).height(10.dp).background(bloco, RoundedCornerShape(5.dp)))
                }
                Box(Modifier.width(72.dp).height(14.dp).background(bloco, RoundedCornerShape(6.dp)))
            }
        }
    }
}

/**
 * Lista vazia (vazio.ts): um ícone que diz do que é a lista, num selo inclinado, um título curto
 * e o que fazer para sair dele. [compacto] para dentro de cartões pequenos.
 */
@Composable
fun Vazio(
    icone: Icone,
    titulo: String,
    modifier: Modifier = Modifier,
    texto: String? = null,
    compacto: Boolean = false,
    acao: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compacto) 16.dp else 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val lado = if (compacto) 44.dp else 68.dp
        Box(
            Modifier
                .size(lado)
                .rotate(-6f)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(if (compacto) 16.dp else 24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icone.desenho),
                null,
                Modifier.size(if (compacto) 20.dp else 28.dp).rotate(6f),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(if (compacto) 4.dp else 8.dp))
        Text(titulo, style = if (compacto) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        texto?.let {
            Text(
                it,
                Modifier.widthIn(max = 340.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        acao?.let { (rotulo, executar) -> Button(executar, Modifier.padding(top = 8.dp)) { Text(rotulo) } }
    }
}

/** Por que uma tela não carregou: muda o ícone, o título e o texto (Estados.swift). */
enum class FalhaAoCarregar { SemInternet, DoServidor, Outra }

/**
 * Erro ao carregar uma tela, com tentar de novo. Sem internet e erro do servidor usam os textos da
 * tela de erro do site; nos outros, a [mensagem] (mensagemDeErro) explica.
 */
@Composable
fun EstadoDeErro(falha: FalhaAoCarregar, tentarDeNovo: () -> Unit, modifier: Modifier = Modifier, mensagem: String? = null) {
    val (icone, titulo, texto) = when (falha) {
        FalhaAoCarregar.SemInternet -> Triple(Icone.Alerta, R.string.erro_sem_internet_titulo, stringResource(R.string.erro_sem_internet_texto))
        FalhaAoCarregar.DoServidor -> Triple(Icone.Alerta, R.string.erro_500_titulo, stringResource(R.string.erro_500_texto))
        FalhaAoCarregar.Outra -> Triple(Icone.Alerta, R.string.erro_carregar_titulo, mensagem.orEmpty())
    }
    Vazio(icone, stringResource(titulo), modifier, texto, acao = stringResource(R.string.tentar_de_novo) to tentarDeNovo)
}

/** Faixa no topo da lista quando o que aparece é o guardado no aparelho (AvisoDeGuardado do iOS). */
@Composable
fun AvisoDeGuardado(quando: String, conectado: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            stringResource(if (conectado) R.string.guardado_sem_atualizar else R.string.guardado_sem_internet, quando),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/**
 * Aba que ainda não é nativa: o título, o aviso e o botão que abre a página no site num Custom
 * Tab (D-090 do iOS). Cada etapa troca pela tela de verdade.
 */
@Composable
fun AbaEmBreve(titulo: String, caminho: String, modifier: Modifier = Modifier) {
    val abrir = lembrarAbrirNoSite()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Porquinho(HumorDoPorquinho.Feliz, largura = 140.dp)
        Text(titulo, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.aba_em_breve),
            Modifier.widthIn(max = 420.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton({ abrir(caminho) }) {
            Text(stringResource(R.string.abrir_no_site))
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(Icones.drawable.ic_abre_fora), stringResource(R.string.abre_no_site), Modifier.size(18.dp))
        }
    }
}
