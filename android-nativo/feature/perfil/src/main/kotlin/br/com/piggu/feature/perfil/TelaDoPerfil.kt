package br.com.piggu.feature.perfil

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.piggu.core.data.Carga
import br.com.piggu.core.designsystem.Icone
import br.com.piggu.core.designsystem.ModoDeCor
import br.com.piggu.core.designsystem.PigguTheme
import br.com.piggu.core.designsystem.VarianteDeCor
import br.com.piggu.core.model.ModuloDeEstiloDeVida
import br.com.piggu.core.model.MoedaDisponivel
import br.com.piggu.core.model.PigguRole
import br.com.piggu.core.model.Usuario
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.EstadoDeErro
import br.com.piggu.core.ui.Esqueleto
import br.com.piggu.core.ui.FalhaAoCarregar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import br.com.piggu.core.ui.R as Comum

data class EventosDoPerfil(
    val voltar: () -> Unit = {},
    val mensagemVista: () -> Unit = {},
    val tentarDeNovo: () -> Unit = {},
    val verFamilia: () -> Unit = {},
    val escolherDaGaleria: () -> Unit = {},
    val tirarFoto: () -> Unit = {},
    val usarFotoDoGoogle: () -> Unit = {},
    val removerFoto: () -> Unit = {},
    val mudarApelido: (String) -> Unit = {},
    val salvarApelido: () -> Unit = {},
    val mudarMoeda: (String) -> Unit = {},
    val mudarMostrarCotacao: (Boolean) -> Unit = {},
    val mudarMoedaCotacao: (String) -> Unit = {},
    val mudarMoedaConversao: (String) -> Unit = {},
    val mudarAvisoPorEmail: (Boolean) -> Unit = {},
    /** A tela pede a permissão das notificações antes de passar ao ViewModel. */
    val mudarAvisoNoCelular: (Boolean) -> Unit = {},
    val mudarMostrarTudoAoParceiro: (Boolean) -> Unit = {},
    val mudarCompartilharPorPadrao: (Boolean) -> Unit = {},
    val organizarPainel: () -> Unit = {},
    val alternarModulo: (ModuloDeEstiloDeVida) -> Unit = {},
    val escolherModo: (ModoDeCor) -> Unit = {},
    val mudarCoresDoCelular: (Boolean) -> Unit = {},
    val escolherCor: (VarianteDeCor) -> Unit = {},
    val dispensarAvisoDoPadrao: () -> Unit = {},
    val escolherIcone: (IconeDoApp) -> Unit = {},
    val abrirTour: () -> Unit = {},
    val retirarAutorizacaoDaNota: () -> Unit = {},
    val retirarAutorizacaoDoAssistente: () -> Unit = {},
    val informarDocumento: () -> Unit = {},
    val mudarSenhaAtual: (String) -> Unit = {},
    val mudarSenhaNova: (String) -> Unit = {},
    val mudarSenhaRepetida: (String) -> Unit = {},
    val salvarSenha: () -> Unit = {},
    val exportar: () -> Unit = {},
    val excluirConta: () -> Unit = {},
    val alterarPapel: (Usuario, PigguRole) -> Unit = { _, _ -> },
    val alternarAtivo: (Usuario) -> Unit = {},
    val sair: () -> Unit = {},
)

/**
 * O Perfil ligado ao [PerfilViewModel]: tela cheia, aberta pelo Mais. O app passa o que é de
 * outras telas: a folha de organizar o Painel, o diálogo de excluir a conta e o aviso de
 * confirmar o e-mail (feature:login), o Premium e o tour.
 */
@Composable
fun TelaDoPerfil(
    voltar: () -> Unit,
    organizarPainel: () -> Unit,
    excluirConta: () -> Unit,
    avisoDoEmail: @Composable () -> Unit,
    abrirPremium: () -> Unit,
    abrirTour: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PerfilViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val resolver = contexto.contentResolver

    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.trocarFoto { resolver.openInputStream(it)?.use { entrada -> entrada.readBytes() } } }
    }
    val fotoDaCamera = remember { File(contexto.cacheDir, "perfil/camera.jpg") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { tirou ->
        if (tirou) viewModel.trocarFoto { fotoDaCamera.readBytes().also { fotoDaCamera.delete() } }
    }
    val permissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitiu ->
        viewModel.mudarAvisoNoCelular(ligado = true, permitido = permitiu)
    }
    val arquivoDosDados = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportar { dados -> resolver.openOutputStream(it)?.use { saida -> saida.write(dados) } } }
    }

    LaunchedEffect(estado.irParaOPremium) {
        if (estado.irParaOPremium) {
            abrirPremium()
            viewModel.premiumAberto()
        }
    }

    val arquivoDosDadosNome = stringResource(R.string.perfil_dados_arquivo)
    TelaDoPerfil(
        estado,
        EventosDoPerfil(
            voltar = voltar,
            mensagemVista = viewModel::mensagemVista,
            tentarDeNovo = viewModel::tentarDeNovo,
            verFamilia = { abrirNoApp(contexto, "piggu://familia") },
            escolherDaGaleria = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            tirarFoto = {
                try {
                    fotoDaCamera.parentFile?.mkdirs()
                    camera.launch(FileProvider.getUriForFile(contexto, "${contexto.packageName}.perfil.arquivos", fotoDaCamera))
                } catch (_: ActivityNotFoundException) {
                    viewModel.semCamera()
                } catch (_: SecurityException) {
                    viewModel.semCamera()
                }
            },
            usarFotoDoGoogle = viewModel::usarFotoDoGoogle,
            removerFoto = viewModel::removerFoto,
            mudarApelido = viewModel::mudarApelido,
            salvarApelido = viewModel::salvarApelido,
            mudarMoeda = viewModel::mudarMoeda,
            mudarMostrarCotacao = viewModel::mudarMostrarCotacao,
            mudarMoedaCotacao = viewModel::mudarMoedaCotacao,
            mudarMoedaConversao = viewModel::mudarMoedaConversao,
            mudarAvisoPorEmail = viewModel::mudarAvisoPorEmail,
            mudarAvisoNoCelular = { ligado ->
                // A permissão do sistema vem antes de gravar: sem ela o aviso no celular fica desligado.
                val permitido = NotificationManagerCompat.from(contexto).areNotificationsEnabled()
                if (ligado && !permitido && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissao.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.mudarAvisoNoCelular(ligado, permitido)
                }
            },
            mudarMostrarTudoAoParceiro = viewModel::mudarMostrarTudoAoParceiro,
            mudarCompartilharPorPadrao = viewModel::mudarCompartilharPorPadrao,
            organizarPainel = organizarPainel,
            alternarModulo = viewModel::alternarModulo,
            escolherModo = viewModel::escolherModo,
            mudarCoresDoCelular = viewModel::mudarCoresDoCelular,
            escolherCor = viewModel::escolherCor,
            dispensarAvisoDoPadrao = viewModel::dispensarAvisoDoPadrao,
            escolherIcone = viewModel::escolherIcone,
            abrirTour = abrirTour,
            retirarAutorizacaoDaNota = viewModel::retirarAutorizacaoDaNota,
            retirarAutorizacaoDoAssistente = viewModel::retirarAutorizacaoDoAssistente,
            informarDocumento = { abrirNoApp(contexto, "piggu://cpf") },
            mudarSenhaAtual = viewModel::mudarSenhaAtual,
            mudarSenhaNova = viewModel::mudarSenhaNova,
            mudarSenhaRepetida = viewModel::mudarSenhaRepetida,
            salvarSenha = viewModel::salvarSenha,
            exportar = { arquivoDosDados.launch(arquivoDosDadosNome) },
            excluirConta = excluirConta,
            alterarPapel = viewModel::alterarPapel,
            alternarAtivo = viewModel::alternarAtivo,
            sair = viewModel::sair,
        ),
        avisoDoEmail = avisoDoEmail,
        modifier = modifier,
        coresDoCelularDisponiveis = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    )
}

/** Uma tela do app pelo link piggu:// (a família e o CPF), que a MainActivity abre. */
private fun abrirNoApp(contexto: Context, link: String) {
    contexto.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()).setPackage(contexto.packageName))
}

/** A tela só desenhada (capturas e prévias). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDoPerfil(
    estado: EstadoDoPerfil,
    eventos: EventosDoPerfil,
    modifier: Modifier = Modifier,
    avisoDoEmail: @Composable () -> Unit = {},
    /** Cores do celular só existem no Android 12 ou mais novo. */
    coresDoCelularDisponiveis: Boolean = true,
) {
    val mensagens = remember { SnackbarHostState() }
    val contexto = LocalContext.current
    LaunchedEffect(estado.aviso, estado.erro) {
        val texto = estado.erro?.texto(contexto) ?: estado.aviso?.let(contexto::getString) ?: return@LaunchedEffect
        mensagens.showSnackbar(texto)
        eventos.mensagemVista()
    }
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.perfil_titulo)) },
                navigationIcon = {
                    IconButton(eventos.voltar) { Icon(painterResource(Icone.Esquerda.desenho), stringResource(Comum.string.voltar)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(mensagens) },
    ) { margens ->
        val usuario = estado.usuario
        Box(Modifier.padding(margens).fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (usuario == null) {
                    Esqueleto(linhas = 8)
                    return@Column
                }
                if (!estado.conectado) Faixa(stringResource(R.string.perfil_sem_internet))
                Identidade(usuario, estado, eventos)
                Moeda(estado, eventos)
                Avisos(usuario, estado, eventos)
                if (estado.temCompanhia) Compartilhamento(estado, eventos)
                Cartao(stringResource(R.string.perfil_painel_titulo), stringResource(R.string.perfil_painel_explicacao)) {
                    OutlinedButton(eventos.organizarPainel) { Text(stringResource(R.string.perfil_painel_organizar)) }
                }
                TelasDoMenu(estado, eventos)
                Aparencia(estado, eventos, coresDoCelularDisponiveis)
                CorDoPiggu(estado, eventos)
                IconeDoAppEscolha(estado, eventos)
                Cartao(stringResource(R.string.perfil_tour_titulo), stringResource(R.string.perfil_tour_explicacao)) {
                    OutlinedButton(eventos.abrirTour) { Text(stringResource(R.string.perfil_tour_ver)) }
                }
                Cartao(stringResource(R.string.perfil_ia_titulo), stringResource(R.string.perfil_ia_explicacao)) {
                    OutlinedButton(eventos.retirarAutorizacaoDaNota, enabled = !estado.ocupado) { Text(stringResource(R.string.perfil_ia_retirar_nota)) }
                    OutlinedButton(eventos.retirarAutorizacaoDoAssistente, enabled = !estado.ocupado) { Text(stringResource(R.string.perfil_ia_retirar_assistente)) }
                }
                Senha(usuario, estado.senha, eventos, avisoDoEmail)
                Cartao(stringResource(R.string.perfil_dados_titulo), stringResource(R.string.perfil_dados_explicacao)) {
                    OutlinedButton(eventos.exportar, enabled = !estado.ocupado) { Text(stringResource(R.string.perfil_dados_baixar)) }
                    Button(
                        eventos.excluirConta,
                        colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError),
                    ) { Text(stringResource(R.string.perfil_excluir_conta)) }
                }
                estado.contas?.let { ContasDaInstalacao(it, eventos) }
                OutlinedButton(eventos.sair, Modifier.fillMaxWidth()) { Text(stringResource(R.string.perfil_sair)) }
            }
        }
    }
}

// Seções.

@Composable
private fun Identidade(usuario: Usuario, estado: EstadoDoPerfil, eventos: EventosDoPerfil) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FotoDaPessoa(usuario.foto, usuario.nome)
                Column(Modifier.weight(1f)) {
                    Text(usuario.nome, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    Text(usuario.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    nomeDoPapel(usuario.role)?.let { Text(stringResource(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                    TextButton(eventos.verFamilia) { Text(stringResource(R.string.perfil_ver_familia)) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ativo = !estado.ocupado
                OutlinedButton(eventos.escolherDaGaleria, enabled = ativo) {
                    Text(stringResource(if (usuario.foto == null) R.string.perfil_colocar_foto else R.string.perfil_trocar_foto))
                }
                OutlinedButton(eventos.tirarFoto, enabled = ativo) { Text(stringResource(R.string.perfil_tirar_foto)) }
                if (usuario.temFotoDoGoogle == true && (usuario.fotoPropria == true || usuario.foto == null)) {
                    OutlinedButton(eventos.usarFotoDoGoogle, enabled = ativo) { Text(stringResource(R.string.perfil_usar_foto_do_google)) }
                }
                if (usuario.foto != null) {
                    OutlinedButton(eventos.removerFoto, enabled = ativo) { Text(stringResource(R.string.perfil_remover_foto)) }
                }
            }
            OutlinedTextField(
                estado.apelido,
                eventos.mudarApelido,
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.perfil_apelido)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { eventos.salvarApelido() }),
            )
            OutlinedButton(eventos.salvarApelido, enabled = !estado.ocupado) { Text(stringResource(R.string.perfil_salvar_apelido)) }
        }
    }
}

/** A foto (a própria em data: ou a do Google) ou, sem ela, a primeira letra do nome. */
@Composable
private fun FotoDaPessoa(foto: String?, nome: String) {
    val imagem by produceState<ImageBitmap?>(null, foto) {
        value = foto?.let { withContext(Dispatchers.IO) { lerFotoDoPerfil(it)?.asImageBitmap() } }
    }
    val descricao = stringResource(R.string.perfil_foto_de, nome)
    val modificador = Modifier.size(76.dp).clip(CircleShape)
    imagem?.let {
        Image(it, descricao, modificador, contentScale = ContentScale.Crop)
    } ?: Box(
        modificador.background(MaterialTheme.colorScheme.primaryContainer).semantics { contentDescription = descricao },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            nome.trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun Moeda(estado: EstadoDoPerfil, eventos: EventosDoPerfil) {
    val p = estado.preferencias
    val ativo = !estado.ocupado && estado.moedas.isNotEmpty()
    Cartao(stringResource(R.string.perfil_moeda_titulo)) {
        Escolha(stringResource(R.string.perfil_moeda_do_app), p.moeda, estado.moedas, ativo, eventos.mudarMoeda)
        Explicacao(stringResource(R.string.perfil_moeda_explicacao))
        Chave(stringResource(R.string.perfil_mostrar_cotacao), p.mostrarCotacao, ativo, eventos.mudarMostrarCotacao)
        if (p.mostrarCotacao) {
            Escolha(stringResource(R.string.perfil_cotacao_entre), p.moedaCotacao ?: p.moeda, estado.moedas, ativo, eventos.mudarMoedaCotacao)
            Escolha(stringResource(R.string.perfil_cotacao_para), p.moedaConversao, estado.moedas, ativo, eventos.mudarMoedaConversao)
            Explicacao(stringResource(R.string.perfil_cotacao_explicacao))
        }
    }
}

@Composable
private fun Avisos(usuario: Usuario, estado: EstadoDoPerfil, eventos: EventosDoPerfil) {
    Cartao(stringResource(R.string.perfil_avisos_titulo), stringResource(R.string.perfil_avisos_explicacao)) {
        Chave(stringResource(R.string.perfil_avisos_email, usuario.email), estado.avisos.email, !estado.ocupado, eventos.mudarAvisoPorEmail)
        Chave(stringResource(R.string.perfil_avisos_celular), estado.avisos.push, !estado.ocupado, eventos.mudarAvisoNoCelular)
    }
}

@Composable
private fun Compartilhamento(estado: EstadoDoPerfil, eventos: EventosDoPerfil) {
    Cartao(stringResource(R.string.perfil_compartilhamento_titulo), stringResource(R.string.perfil_compartilhamento_explicacao)) {
        estado.parceiro?.let {
            Chave(
                stringResource(R.string.perfil_parceiro_ve_tudo, it.nome),
                estado.compartilhamento.mostraTudoAoParceiro,
                !estado.ocupado,
                eventos.mudarMostrarTudoAoParceiro,
            )
        }
        Chave(
            stringResource(R.string.perfil_compartilhar_por_padrao),
            estado.compartilhamento.compartilharPorPadrao,
            !estado.ocupado,
            eventos.mudarCompartilharPorPadrao,
        )
    }
}

@Composable
private fun TelasDoMenu(estado: EstadoDoPerfil, eventos: EventosDoPerfil) {
    Cartao(stringResource(R.string.perfil_modulos_titulo), stringResource(R.string.perfil_modulos_explicacao)) {
        MODULOS.forEach { modulo ->
            Chave(stringResource(nomeDoModulo(modulo)), modulo in estado.modulos, !estado.ocupado) { eventos.alternarModulo(modulo) }
        }
    }
}

@Composable
private fun Aparencia(estado: EstadoDoPerfil, eventos: EventosDoPerfil, coresDoCelularDisponiveis: Boolean) {
    Cartao(stringResource(R.string.perfil_aparencia_titulo), stringResource(R.string.perfil_aparencia_explicacao)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ModoDeCor.entries.forEachIndexed { i, modo ->
                SegmentedButton(estado.modo == modo, { eventos.escolherModo(modo) }, SegmentedButtonDefaults.itemShape(i, ModoDeCor.entries.size)) {
                    Text(stringResource(modo.nome), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (coresDoCelularDisponiveis) {
            Chave(stringResource(R.string.perfil_cores_do_celular), estado.coresDoCelular, true, eventos.mudarCoresDoCelular)
            Explicacao(stringResource(R.string.perfil_cores_do_celular_explicacao))
        }
    }
}

@Composable
private fun CorDoPiggu(estado: EstadoDoPerfil, eventos: EventosDoPerfil) {
    Cartao(stringResource(R.string.perfil_cor_titulo), stringResource(R.string.perfil_cor_explicacao)) {
        if (!estado.premium) {
            Text(stringResource(R.string.perfil_mais_cores_no_premium), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (estado.voltouAoPadrao) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(stringResource(R.string.perfil_voltou_ao_padrao), color = MaterialTheme.colorScheme.onSecondaryContainer)
                    TextButton(eventos.dispensarAvisoDoPadrao) { Text(stringResource(R.string.perfil_entendi)) }
                }
            }
        }
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VarianteDeCor.entries.forEach { cor -> AmostraDeCor(cor, estado, eventos) }
        }
    }
}

@Composable
private fun AmostraDeCor(cor: VarianteDeCor, estado: EstadoDoPerfil, eventos: EventosDoPerfil) {
    val travada = cor.premium && !estado.premium
    val escolhida = cor == estado.cor && !estado.coresDoCelular
    val nome = stringResource(cor.nome)
    Surface(
        Modifier.width(156.dp).selectable(escolhida, role = Role.RadioButton) { eventos.escolherCor(cor) }
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(if (escolhida) 2.dp else 1.dp, if (escolhida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(10.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            // A amostra é a cor de destaque da própria variante, no modo da tela.
            PigguTheme(cor, estado.modo) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(nome, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (travada) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(Icone.Estrela.desenho), null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.perfil_premium), style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Text(stringResource(cor.descricao), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun IconeDoAppEscolha(estado: EstadoDoPerfil, eventos: EventosDoPerfil) {
    Cartao(stringResource(R.string.perfil_icone_titulo), stringResource(R.string.perfil_icone_explicacao)) {
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconeDoApp.entries.forEach { icone ->
                val travado = icone.premium && !estado.premium
                val escolhido = icone == estado.icone
                val nome = stringResource(icone.nome)
                val descricao = if (travado) stringResource(R.string.perfil_do_premium, nome) else nome
                Column(
                    Modifier.width(76.dp).selectable(escolhido, role = Role.RadioButton) { eventos.escolherIcone(icone) }
                        .semantics(mergeDescendants = true) { contentDescription = descricao },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Image(
                            painterResource(icone.previa),
                            null,
                            Modifier.padding(4.dp).size(56.dp).clip(RoundedCornerShape(13.dp))
                                .border(3.dp, if (escolhido) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(13.dp))
                                .alpha(if (travado) 0.6f else 1f),
                        )
                        if (travado) {
                            Icon(
                                painterResource(Icone.Estrela.desenho),
                                null,
                                Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(3.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    Text(nome, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Senha(usuario: Usuario, f: FormularioDeSenha, eventos: EventosDoPerfil, avisoDoEmail: @Composable () -> Unit) {
    val temSenha = usuario.temSenha == true
    val explicacao = stringResource(if (temSenha) R.string.perfil_senha_tem else R.string.perfil_senha_nao_tem, usuario.email)
    Cartao(stringResource(R.string.perfil_senha_titulo), explicacao) {
        if (usuario.emailConfirmado == false) avisoDoEmail()
        if (usuario.cpfInformado == false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.perfil_documento_pendente), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(eventos.informarDocumento) { Text(stringResource(R.string.perfil_informar_agora)) }
            }
        }
        f.erro?.let {
            Text(it.texto(LocalContext.current), Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, color = MaterialTheme.colorScheme.error)
        }
        f.aviso?.let {
            Text(stringResource(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.primary)
        }
        if (temSenha) CampoDeSenha(f.atual, eventos.mudarSenhaAtual, stringResource(R.string.perfil_senha_atual), ImeAction.Next) {}
        CampoDeSenha(f.nova, eventos.mudarSenhaNova, stringResource(R.string.perfil_senha_nova), ImeAction.Next) {}
        CampoDeSenha(f.repita, eventos.mudarSenhaRepetida, stringResource(R.string.perfil_senha_repita), ImeAction.Done, eventos.salvarSenha)
        Explicacao(stringResource(R.string.perfil_senha_dica))
        Button(eventos.salvarSenha, enabled = !f.salvando) {
            Text(stringResource(if (temSenha) R.string.perfil_senha_trocar else R.string.perfil_senha_criar))
        }
    }
}

@Composable
private fun CampoDeSenha(valor: String, mudar: (String) -> Unit, rotulo: String, acao: ImeAction, aoTerminar: () -> Unit) {
    OutlinedTextField(
        valor,
        mudar,
        Modifier.fillMaxWidth(),
        label = { Text(rotulo) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = acao),
        keyboardActions = KeyboardActions(onDone = { aoTerminar() }),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContasDaInstalacao(contas: Carga<List<Usuario>>, eventos: EventosDoPerfil) {
    Cartao(stringResource(R.string.perfil_contas_titulo)) {
        when (contas) {
            Carga.Carregando -> Esqueleto(linhas = 2)
            is Carga.Falhou -> EstadoDeErro(falhaDe(contas.erro), eventos.tentarDeNovo, mensagem = mensagemDeErro(contas.erro).texto(LocalContext.current))
            is Carga.Pronto -> {
                if (contas.valor.isEmpty()) Explicacao(stringResource(R.string.perfil_contas_vazio))
                contas.valor.forEach { conta ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(conta.nome, style = MaterialTheme.typography.bodyLarge)
                        Text(conta.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            var aberto by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(aberto, { aberto = it }, Modifier.weight(1f)) {
                                OutlinedTextField(
                                    nomeDoPapel(conta.role)?.let { stringResource(it) } ?: conta.role.name,
                                    {},
                                    Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                    readOnly = true,
                                    singleLine = true,
                                    label = { Text(stringResource(R.string.perfil_contas_papel_de, conta.nome)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(aberto) },
                                )
                                ExposedDropdownMenu(aberto, { aberto = false }) {
                                    PAPEIS.forEach { papel ->
                                        DropdownMenuItem({ Text(stringResource(nomeDoPapel(papel)!!)) }, {
                                            aberto = false
                                            if (papel != conta.role) eventos.alterarPapel(conta, papel)
                                        })
                                    }
                                }
                            }
                            OutlinedButton({ eventos.alternarAtivo(conta) }) {
                                Text(stringResource(if (conta.ativo) R.string.perfil_contas_desativar else R.string.perfil_contas_ativar))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Peças.

@Composable
private fun Cartao(titulo: String, explicacao: String? = null, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            explicacao?.let { Explicacao(it) }
            conteudo()
        }
    }
}

@Composable
private fun Explicacao(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Linha inteira tocável com a chave, como a linha-chave do site. */
@Composable
private fun Chave(texto: String, ligada: Boolean, ativa: Boolean, mudar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(ligada, enabled = ativa, role = Role.Switch, onValueChange = mudar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texto, Modifier.weight(1f).padding(end = 12.dp))
        Switch(ligada, onCheckedChange = null, enabled = ativa)
    }
}

/** Uma moeda da lista ("USD · Dólar americano"); enquanto a lista não chega, só o código gravado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Escolha(rotulo: String, codigo: String, moedas: List<MoedaDisponivel>, ativa: Boolean, escolher: (String) -> Unit) {
    var aberta by remember { mutableStateOf(false) }
    val texto = moedas.firstOrNull { it.codigo == codigo }?.let { stringResource(R.string.perfil_moeda_opcao, it.codigo, it.nome) } ?: codigo
    ExposedDropdownMenuBox(aberta, { aberta = it && ativa }) {
        OutlinedTextField(
            texto,
            {},
            Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, ativa),
            readOnly = true,
            enabled = ativa,
            singleLine = true,
            label = { Text(rotulo) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(aberta) },
        )
        ExposedDropdownMenu(aberta, { aberta = false }) {
            moedas.forEach { moeda ->
                DropdownMenuItem({ Text(stringResource(R.string.perfil_moeda_opcao, moeda.codigo, moeda.nome)) }, {
                    aberta = false
                    if (moeda.codigo != codigo) escolher(moeda.codigo)
                })
            }
        }
    }
}

@Composable
private fun Faixa(texto: String) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
        Text(
            texto,
            Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun nomeDoPapel(papel: PigguRole): Int? = when (papel) {
    PigguRole.ADMIN -> R.string.perfil_papel_admin
    PigguRole.TITULAR -> R.string.perfil_papel_titular
    PigguRole.PARCEIRO -> R.string.perfil_papel_parceiro
    PigguRole.MEMBRO -> R.string.perfil_papel_membro
    PigguRole.DESCONHECIDO -> null
}

private fun nomeDoModulo(modulo: ModuloDeEstiloDeVida): Int = when (modulo) {
    ModuloDeEstiloDeVida.COMPRAS -> R.string.perfil_modulo_compras
    ModuloDeEstiloDeVida.LUGARES -> R.string.perfil_modulo_lugares
    ModuloDeEstiloDeVida.FILMES -> R.string.perfil_modulo_filmes
    ModuloDeEstiloDeVida.FOTOS -> R.string.perfil_modulo_fotos
    ModuloDeEstiloDeVida.PREMIOS -> R.string.perfil_modulo_premios
    ModuloDeEstiloDeVida.GIRLS_MATH, ModuloDeEstiloDeVida.DESCONHECIDO -> R.string.perfil_modulo_girls_math
}

private fun falhaDe(erro: ErroDaApi): FalhaAoCarregar = when {
    erro is ErroDaApi.SemInternet -> FalhaAoCarregar.SemInternet
    erro is ErroDaApi.DoServidor && erro.status >= 500 -> FalhaAoCarregar.DoServidor
    else -> FalhaAoCarregar.Outra
}
