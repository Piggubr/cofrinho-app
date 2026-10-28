package br.com.piggu.feature.perfil

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.piggu.core.data.Carga
import br.com.piggu.core.data.EstadoDaSessao
import br.com.piggu.core.data.FilaDeGastos
import br.com.piggu.core.data.ListasParaLancar
import br.com.piggu.core.data.MonitorDeRede
import br.com.piggu.core.data.Sessao
import br.com.piggu.core.data.definirSenha
import br.com.piggu.core.data.ehAdmin
import br.com.piggu.core.data.ehPremium
import br.com.piggu.core.data.ehTitular
import br.com.piggu.core.datastore.CofreDaSessao
import br.com.piggu.core.datastore.PreferenciasDoAparelho
import br.com.piggu.core.designsystem.ModoDeCor
import br.com.piggu.core.designsystem.VarianteDeCor
import br.com.piggu.core.model.Avisos
import br.com.piggu.core.model.Compartilhamento
import br.com.piggu.core.model.MembroDaFamilia
import br.com.piggu.core.model.ModuloDeEstiloDeVida
import br.com.piggu.core.model.MoedaDisponivel
import br.com.piggu.core.model.PedidoDeAlteracaoDeUsuario
import br.com.piggu.core.model.PedidoDeCompartilhamento
import br.com.piggu.core.model.PedidoDeFoto
import br.com.piggu.core.model.PedidoDeModulos
import br.com.piggu.core.model.PedidoDePreferencias
import br.com.piggu.core.model.PedidoSobreVoce
import br.com.piggu.core.model.PigguRole
import br.com.piggu.core.model.Usuario
import br.com.piggu.core.network.ErroDaApi
import br.com.piggu.core.network.MensagemDeErro
import br.com.piggu.core.network.ServicoDeFinancas
import br.com.piggu.core.network.ServicoDeIdentidade
import br.com.piggu.core.network.mensagemDeErro
import br.com.piggu.core.ui.formatos.ProblemaDaSenha
import br.com.piggu.core.ui.formatos.problemaDaSenha
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Os seis módulos de estilo de vida, na ordem do menu (D-092). */
val MODULOS: List<ModuloDeEstiloDeVida> = ModuloDeEstiloDeVida.entries - ModuloDeEstiloDeVida.DESCONHECIDO

/** Os papéis que o administrador escolhe nas contas da instalação. */
val PAPEIS = listOf(PigguRole.ADMIN, PigguRole.TITULAR, PigguRole.PARCEIRO, PigguRole.MEMBRO)

/** Senha de acesso, com o erro e o aviso no próprio cartão: o topo da tela fica longe dali. */
data class FormularioDeSenha(
    val atual: String = "",
    val nova: String = "",
    val repita: String = "",
    val salvando: Boolean = false,
    val erro: MensagemDeErro? = null,
    @param:StringRes val aviso: Int? = null,
)

data class EstadoDoPerfil(
    /** Nulo enquanto a sessão abre: a tela mostra o esqueleto. */
    val usuario: Usuario? = null,
    val conectado: Boolean = true,
    val moedas: List<MoedaDisponivel> = emptyList(),
    val apelido: String = "",
    val preferencias: PedidoDePreferencias = PedidoDePreferencias(moeda = "EUR", moedaCotacao = "EUR", moedaConversao = "BRL", mostrarCotacao = true),
    val avisos: Avisos = Avisos(email = false, push = false),
    /** Há mais alguém na família: aparece o Compartilhamento. */
    val temCompanhia: Boolean = false,
    /** O outro do casal (pessoas-da-familia.ts); sem ele, só a chave do "já nascem compartilhados". */
    val parceiro: MembroDaFamilia? = null,
    val compartilhamento: Compartilhamento = Compartilhamento(mostraTudoAoParceiro = false, compartilharPorPadrao = false),
    val modulos: Set<ModuloDeEstiloDeVida> = MODULOS.toSet(),
    val cor: VarianteDeCor = VarianteDeCor.Chiclete,
    val modo: ModoDeCor = ModoDeCor.Automatico,
    val coresDoCelular: Boolean = false,
    val voltouAoPadrao: Boolean = false,
    val icone: IconeDoApp = IconeDoApp.Padrao,
    val senha: FormularioDeSenha = FormularioDeSenha(),
    /** Contas da instalação: nulo para quem não é o administrador. */
    val contas: Carga<List<Usuario>>? = null,
    /** Um pedido em andamento: as chaves e os botões esperam. */
    val ocupado: Boolean = false,
    @param:StringRes val aviso: Int? = null,
    val erro: MensagemDeErro? = null,
    /** O titular sem Premium tocou numa personalização do Premium: a tela abre o Premium. */
    val irParaOPremium: Boolean = false,
) {
    val premium: Boolean get() = usuario?.ehPremium == true
}

/**
 * Perfil (features/profile e PerfilView do iOS): a foto e o nome, a moeda, os avisos, o
 * compartilhamento, as telas do menu, a aparência, o ícone do app, a IA, a senha, os dados e,
 * para o administrador, as contas da instalação. Cada chave já salva, sem botão, e volta ao que
 * está gravado quando o servidor recusa.
 */
@HiltViewModel
class PerfilViewModel internal constructor(
    private val sessao: Sessao,
    private val identidade: ServicoDeIdentidade,
    private val financas: ServicoDeFinancas,
    listas: ListasParaLancar,
    private val preferenciasDoAparelho: PreferenciasDoAparelho,
    private val icones: IconesDoApp,
    private val cofre: CofreDaSessao,
    private val fila: FilaDeGastos,
    conectado: StateFlow<Boolean>,
) : ViewModel() {
    @Inject constructor(
        sessao: Sessao,
        identidade: ServicoDeIdentidade,
        financas: ServicoDeFinancas,
        listas: ListasParaLancar,
        preferenciasDoAparelho: PreferenciasDoAparelho,
        icones: IconesDoApp,
        cofre: CofreDaSessao,
        fila: FilaDeGastos,
        rede: MonitorDeRede,
    ) : this(sessao, identidade, financas, listas, preferenciasDoAparelho, icones, cofre, fila, rede.conectado)

    private val estadoAtual = MutableStateFlow(formulariosDe(EstadoDoPerfil(icone = icones.atual()), sessao.usuario))
    val estado: StateFlow<EstadoDoPerfil> = estadoAtual.asStateFlow()

    init {
        viewModelScope.launch {
            sessao.estado.collect { sessao ->
                val usuario = (sessao as? EstadoDaSessao.Logado)?.usuario ?: return@collect
                // A primeira vez preenche os campos; depois só o perfil muda (foto, senha, plano).
                val primeira = estadoAtual.value.usuario == null
                estadoAtual.update { if (it.usuario == null) formulariosDe(it, usuario) else it.copy(usuario = usuario) }
                if (primeira && estadoAtual.value.conectado) carregar()
            }
        }
        viewModelScope.launch {
            combine(
                preferenciasDoAparelho.cor,
                preferenciasDoAparelho.modo,
                preferenciasDoAparelho.coresDoCelular,
                preferenciasDoAparelho.voltouAoPadrao,
            ) { cor, modo, coresDoCelular, voltou ->
                estadoAtual.update {
                    it.copy(
                        cor = VarianteDeCor.deId(cor).efetiva(it.usuario?.ehPremium),
                        modo = ModoDeCor.deId(modo),
                        coresDoCelular = coresDoCelular,
                        voltouAoPadrao = voltou,
                    )
                }
            }.collect()
        }
        viewModelScope.launch {
            listas.familia().collect { carga ->
                if (carga !is Carga.Pronto) return@collect
                val membros = carga.valor.membros
                estadoAtual.update { it.copy(temCompanhia = membros.size > 1, parceiro = parceiroDe(it.usuario, membros)) }
            }
        }
        // Sem internet não adianta pedir; quando ela volta, busca o que faltou.
        viewModelScope.launch {
            conectado.collect { agora ->
                estadoAtual.update { it.copy(conectado = agora) }
                if (agora) carregar()
            }
        }
    }

    private fun carregar() {
        val e = estadoAtual.value
        if (e.moedas.isEmpty()) {
            viewModelScope.launch {
                try {
                    val moedas = financas.listarMoedas()
                    estadoAtual.update { it.copy(moedas = moedas) }
                } catch (falha: ErroDaApi) {
                    estadoAtual.update { it.copy(erro = mensagemDeErro(falha)) }
                }
            }
        }
        if (e.usuario?.ehAdmin == true && e.contas !is Carga.Pronto) carregarContas()
    }

    fun tentarDeNovo() = carregar()

    /** A Snackbar mostrou o aviso ou o erro. */
    fun mensagemVista() = estadoAtual.update { it.copy(aviso = null, erro = null) }

    fun premiumAberto() = estadoAtual.update { it.copy(irParaOPremium = false) }

    // Foto e nome.

    /** Lê a foto escolhida (galeria ou câmera), recorta, reduz e envia. */
    fun trocarFoto(ler: suspend () -> ByteArray?) {
        if (estadoAtual.value.ocupado) return
        estadoAtual.update { it.copy(ocupado = true, erro = null, aviso = null) }
        viewModelScope.launch {
            val base64 = withContext(Dispatchers.Default) {
                try {
                    ler()?.let(::prepararFotoDePerfil)
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }
            if (base64 == null) {
                estadoAtual.update { it.copy(ocupado = false, erro = MensagemDeErro.DoApp(R.string.perfil_foto_ilegivel)) }
            } else {
                salvar(R.string.perfil_foto_trocada) { identidade.trocarFoto(PedidoDeFoto(base64)) }
            }
        }
    }

    /** O aparelho não tem app de câmera (ou outro app pediu a permissão da câmera). */
    fun semCamera() = estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(R.string.perfil_sem_camera), aviso = null) }

    fun removerFoto() =salvar(R.string.perfil_foto_removida) { identidade.removerFoto() }

    fun usarFotoDoGoogle() = salvar(R.string.perfil_foto_do_google) { identidade.usarFotoDoGoogle() }

    fun mudarApelido(apelido: String) = estadoAtual.update { it.copy(apelido = apelido.take(120)) }

    fun salvarApelido() {
        val apelido = estadoAtual.value.apelido.trim()
        if (apelido.isEmpty()) {
            estadoAtual.update { it.copy(erro = MensagemDeErro.DoApp(R.string.perfil_apelido_vazio), aviso = null) }
            return
        }
        salvar(R.string.perfil_apelido_salvo) { identidade.sobreVoce(PedidoSobreVoce(apelido)) }
    }

    // Moeda: cada escolha já salva.

    fun mudarMoeda(codigo: String) = salvarPreferencias { it.copy(moeda = codigo) }
    fun mudarMoedaCotacao(codigo: String) = salvarPreferencias { it.copy(moedaCotacao = codigo) }
    fun mudarMoedaConversao(codigo: String) = salvarPreferencias { it.copy(moedaConversao = codigo) }
    fun mudarMostrarCotacao(mostrar: Boolean) = salvarPreferencias { it.copy(mostrarCotacao = mostrar) }

    /** Sem recado de sucesso, como o site: a cotação do topo aparece ou some na hora. */
    private fun salvarPreferencias(mudar: (PedidoDePreferencias) -> PedidoDePreferencias) {
        if (estadoAtual.value.ocupado) return
        val pedido = mudar(estadoAtual.value.preferencias)
        estadoAtual.update { it.copy(preferencias = pedido) }
        salvar(null) { identidade.salvarPreferencias(pedido) }
    }

    // Avisos.

    fun mudarAvisoPorEmail(ligado: Boolean) = salvarAvisos(estadoAtual.value.avisos.copy(email = ligado), recusou = false)

    /**
     * [permitido]: o Android deixou o Piggu notificar (a permissão do Android 13 ou as
     * notificações ligadas nas configurações). Sem ela, o celular fica desligado e o e-mail
     * salva mesmo assim. O registro do token do aparelho chega na A7.
     */
    fun mudarAvisoNoCelular(ligado: Boolean, permitido: Boolean) =
        salvarAvisos(estadoAtual.value.avisos.copy(push = ligado && permitido), recusou = ligado && !permitido)

    private fun salvarAvisos(avisos: Avisos, recusou: Boolean) {
        if (estadoAtual.value.ocupado) return
        estadoAtual.update { it.copy(avisos = avisos) }
        salvar(
            R.string.perfil_avisos_salvos,
            depois = { if (recusou) it.copy(aviso = null, erro = MensagemDeErro.DoApp(R.string.perfil_avisos_sem_permissao)) else it },
        ) { identidade.salvarAvisos(avisos) }
    }

    // Compartilhamento.

    fun mudarMostrarTudoAoParceiro(ligado: Boolean) =
        salvarCompartilhamento(estadoAtual.value.compartilhamento.copy(mostraTudoAoParceiro = ligado))

    fun mudarCompartilharPorPadrao(ligado: Boolean) =
        salvarCompartilhamento(estadoAtual.value.compartilhamento.copy(compartilharPorPadrao = ligado))

    private fun salvarCompartilhamento(compartilhamento: Compartilhamento) {
        if (estadoAtual.value.ocupado) return
        estadoAtual.update { it.copy(compartilhamento = compartilhamento) }
        val mostrar = compartilhamento.mostraTudoAoParceiro && estadoAtual.value.parceiro != null
        salvar(R.string.perfil_compartilhamento_salvo) {
            identidade.salvarCompartilhamento(PedidoDeCompartilhamento(mostrar, compartilhamento.compartilharPorPadrao))
        }
    }

    // Telas do menu (D-092).

    fun alternarModulo(modulo: ModuloDeEstiloDeVida) {
        if (estadoAtual.value.ocupado) return
        val atuais = estadoAtual.value.modulos
        val novos = if (modulo in atuais) atuais - modulo else atuais + modulo
        estadoAtual.update { it.copy(modulos = novos) }
        salvar(null) { identidade.salvarModulos(PedidoDeModulos(MODULOS.filter { it in novos })) }
    }

    // Aparência, cor e ícone: deste aparelho.

    fun escolherModo(modo: ModoDeCor) {
        viewModelScope.launch { preferenciasDoAparelho.mudarModo(modo.id) }
    }

    /** A cor do Premium sem Premium convida; escolher uma cor desliga as Cores do celular, que a esconderiam. */
    fun escolherCor(cor: VarianteDeCor) {
        if (cor.premium && !estadoAtual.value.premium) return convidarParaOPremium()
        viewModelScope.launch {
            preferenciasDoAparelho.mudarCor(cor.id)
            if (estadoAtual.value.coresDoCelular) preferenciasDoAparelho.mudarCoresDoCelular(false)
        }
    }

    fun mudarCoresDoCelular(ligadas: Boolean) {
        viewModelScope.launch { preferenciasDoAparelho.mudarCoresDoCelular(ligadas) }
    }

    fun dispensarAvisoDoPadrao() {
        viewModelScope.launch { preferenciasDoAparelho.esquecerQueVoltouAoPadrao() }
    }

    fun escolherIcone(icone: IconeDoApp) {
        if (icone.premium && !estadoAtual.value.premium) return convidarParaOPremium()
        icones.escolher(icone)
        estadoAtual.update { it.copy(icone = icones.atual()) }
    }

    /** O titular vai para o Premium; quem não assina pela família recebe o recado. */
    private fun convidarParaOPremium() = estadoAtual.update {
        if (it.usuario?.ehTitular == true) it.copy(irParaOPremium = true) else it.copy(aviso = R.string.perfil_premium_do_titular, erro = null)
    }

    // Autorizações da IA (autorizacoes-de-ia.ts).

    fun retirarAutorizacaoDaNota() = executar(R.string.perfil_ia_nota_retirada) { financas.retirarAutorizacaoDaLeituraDeNota() }

    fun retirarAutorizacaoDoAssistente() = executar(R.string.perfil_ia_assistente_retirada) { financas.retirarAutorizacaoDoAssistente() }

    // Senha de acesso.

    fun mudarSenhaAtual(senha: String) = mudarSenha { it.copy(atual = senha) }
    fun mudarSenhaNova(senha: String) = mudarSenha { it.copy(nova = senha) }
    fun mudarSenhaRepetida(senha: String) = mudarSenha { it.copy(repita = senha) }

    private fun mudarSenha(mudar: (FormularioDeSenha) -> FormularioDeSenha) =
        estadoAtual.update { it.copy(senha = mudar(it.senha).copy(erro = null, aviso = null)) }

    /** Cria a senha (conta que só entrava pelo Google) ou troca a atual, mantendo este aparelho conectado. */
    fun salvarSenha() {
        val usuario = estadoAtual.value.usuario ?: return
        val f = estadoAtual.value.senha
        if (f.salvando) return
        val trocando = usuario.temSenha == true
        val problema = when {
            trocando && f.atual.isEmpty() -> R.string.perfil_senha_informe_a_atual
            else -> problemaDaSenha(f.nova, usuario.email)?.let(::textoDoProblema)
                ?: R.string.perfil_senha_diferentes.takeIf { f.nova != f.repita }
        }
        if (problema != null) {
            estadoAtual.update { it.copy(senha = it.senha.copy(erro = MensagemDeErro.DoApp(problema), aviso = null)) }
            return
        }
        estadoAtual.update { it.copy(senha = f.copy(salvando = true, erro = null, aviso = null)) }
        viewModelScope.launch {
            try {
                sessao.definirSenha(identidade, cofre, f.nova, if (trocando) f.atual else null)
                estadoAtual.update {
                    it.copy(senha = FormularioDeSenha(aviso = if (trocando) R.string.perfil_senha_trocada else R.string.perfil_senha_criada))
                }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(senha = it.senha.copy(salvando = false, erro = mensagemDeErro(falha))) }
            }
        }
    }

    // Seus dados.

    /** Baixa a cópia dos dados e entrega a [gravar], que escreve no arquivo escolhido pela pessoa. */
    fun exportar(gravar: suspend (ByteArray) -> Unit) {
        if (estadoAtual.value.ocupado) return
        estadoAtual.update { it.copy(ocupado = true, erro = null, aviso = null) }
        viewModelScope.launch {
            val erro = try {
                withContext(Dispatchers.IO) { gravar(identidade.exportarMeusDados().use { it.bytes() }) }
                null
            } catch (falha: ErroDaApi) {
                mensagemDeErro(falha)
            } catch (_: IOException) {
                // shortcut: o arquivo escolhido fica vazio quando a cópia falha no meio; apagar pelo DocumentsContract se incomodar.
                MensagemDeErro.DoApp(R.string.perfil_dados_nao_gravou)
            }
            estadoAtual.update { it.copy(ocupado = false, erro = erro, aviso = if (erro == null) R.string.perfil_dados_salvos else null) }
        }
    }

    // Contas da instalação (só o administrador).

    fun alterarPapel(conta: Usuario, papel: PigguRole) = alterarConta(conta, PedidoDeAlteracaoDeUsuario(role = papel))

    fun alternarAtivo(conta: Usuario) = alterarConta(conta, PedidoDeAlteracaoDeUsuario(ativo = !conta.ativo))

    private fun alterarConta(conta: Usuario, mudancas: PedidoDeAlteracaoDeUsuario) {
        viewModelScope.launch {
            try {
                identidade.alterarUsuario(conta.id, mudancas)
                carregarContas()
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(erro = mensagemDeErro(falha), aviso = null) }
            }
        }
    }

    private fun carregarContas() {
        viewModelScope.launch {
            if (estadoAtual.value.contas !is Carga.Pronto) estadoAtual.update { it.copy(contas = Carga.Carregando) }
            val contas = try {
                Carga.Pronto(identidade.listarUsuarios())
            } catch (falha: ErroDaApi) {
                Carga.Falhou(falha)
            }
            estadoAtual.update { it.copy(contas = contas) }
        }
    }

    /** "Sair" pelo botão também apaga os gastos guardados sem internet desta pessoa (D-108). */
    fun sair() {
        viewModelScope.launch {
            fila.apagarDaPessoa()
            sessao.sair()
        }
    }

    // Apoio.

    /**
     * Grava no servidor e passa o perfil novo à sessão. Recusado, os campos voltam para o que está
     * gravado: não podem mostrar o que não salvou.
     */
    private fun salvar(
        @StringRes aviso: Int?,
        depois: (EstadoDoPerfil) -> EstadoDoPerfil = { it },
        pedido: suspend () -> Usuario,
    ) {
        estadoAtual.update { it.copy(ocupado = true, erro = null, aviso = null) }
        viewModelScope.launch {
            try {
                sessao.entrou(pedido())
                estadoAtual.update { depois(it.copy(ocupado = false, aviso = aviso)) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { formulariosDe(it, sessao.usuario, soAsChaves = true).copy(ocupado = false, erro = mensagemDeErro(falha)) }
            }
        }
    }

    private fun executar(@StringRes aviso: Int, acao: suspend () -> Unit) {
        if (estadoAtual.value.ocupado) return
        estadoAtual.update { it.copy(ocupado = true, erro = null, aviso = null) }
        viewModelScope.launch {
            try {
                acao()
                estadoAtual.update { it.copy(ocupado = false, aviso = aviso) }
            } catch (falha: ErroDaApi) {
                estadoAtual.update { it.copy(ocupado = false, erro = mensagemDeErro(falha)) }
            }
        }
    }
}

/** Os campos com o que está gravado no perfil; [soAsChaves] mantém o apelido que a pessoa digitava. */
private fun formulariosDe(estado: EstadoDoPerfil, usuario: Usuario?, soAsChaves: Boolean = false): EstadoDoPerfil {
    if (usuario == null) return estado
    val prefs = usuario.preferencias
    val moeda = prefs?.moeda ?: "EUR"
    return estado.copy(
        usuario = usuario,
        apelido = if (soAsChaves) estado.apelido else usuario.apelido.orEmpty(),
        // Conta antiga, sem a ponta "de" da cotação, usa a moeda do app.
        preferencias = PedidoDePreferencias(moeda, prefs?.moedaCotacao ?: moeda, prefs?.moedaConversao ?: "BRL", prefs?.mostrarCotacao ?: true),
        avisos = usuario.avisos ?: Avisos(email = false, push = false),
        compartilhamento = usuario.compartilhamento ?: Compartilhamento(mostraTudoAoParceiro = false, compartilharPorPadrao = false),
        // Ausente (conta antiga, demo) liga todos, como no site.
        modulos = prefs?.modulos?.filter { it != ModuloDeEstiloDeVida.DESCONHECIDO }?.toSet() ?: MODULOS.toSet(),
        contas = if (usuario.ehAdmin) estado.contas ?: Carga.Carregando else null,
    )
}

/** O outro do casal: para o titular, o parceiro; para o parceiro, o titular (pessoas-da-familia.ts). */
private fun parceiroDe(eu: Usuario?, membros: List<MembroDaFamilia>): MembroDaFamilia? {
    val outros = membros.filter { it.id != eu?.id }
    val candidatos = outros.filter {
        when (eu?.role) {
            PigguRole.PARCEIRO -> it.papel == PigguRole.TITULAR || it.papel == PigguRole.ADMIN
            PigguRole.TITULAR, PigguRole.ADMIN -> it.papel == PigguRole.PARCEIRO
            else -> false
        }
    }
    return candidatos.firstOrNull { it.papel == PigguRole.TITULAR } ?: candidatos.firstOrNull()
}

@StringRes
private fun textoDoProblema(problema: ProblemaDaSenha): Int = when (problema) {
    ProblemaDaSenha.Curta -> R.string.perfil_senha_curta
    ProblemaDaSenha.Longa -> R.string.perfil_senha_longa
    ProblemaDaSenha.SemLetraOuNumero -> R.string.perfil_senha_letras_e_numeros
    ProblemaDaSenha.IgualAoEmail -> R.string.perfil_senha_igual_ao_email
}
