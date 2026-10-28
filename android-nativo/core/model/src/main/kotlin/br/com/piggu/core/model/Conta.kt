package br.com.piggu.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Tipos do piggu-identity (docs/api/identity.yaml), com os nomes do models.ts do site.
// Regra de nulos em todos os arquivos: o que o models.ts dá como sempre presente é não nulo;
// o resto (e o que o Modelos.swift já viu chegar nulo) é nulo com padrão null. Lista
// ausente vira vazia. Datas ficam em texto, como no site e no iOS.

/** ADMIN opera a instalação; TITULAR é o dono da família; MEMBRO foi convidado. */
@Serializable(with = PigguRole.Serializador::class)
enum class PigguRole {
    ADMIN, TITULAR, PARCEIRO, MEMBRO, DESCONHECIDO;

    object Serializador : SerializadorDeEnum<PigguRole>(entries, DESCONHECIDO)
}

@Serializable(with = Plano.Serializador::class)
enum class Plano {
    GRATUITO, PREMIUM, DESCONHECIDO;

    object Serializador : SerializadorDeEnum<Plano>(entries, DESCONHECIDO)
}

@Serializable(with = Periodo.Serializador::class)
enum class Periodo {
    MENSAL, ANUAL, CASAL_MENSAL, CASAL_ANUAL, FAMILIA_MENSAL, FAMILIA_ANUAL, DESCONHECIDO;

    object Serializador : SerializadorDeEnum<Periodo>(entries, DESCONHECIDO)
}

/** Telas de estilo de vida que a pessoa liga no menu; no JSON em minúsculas ("girls-math"). */
@Serializable(with = ModuloDeEstiloDeVida.Serializador::class)
enum class ModuloDeEstiloDeVida {
    COMPRAS, LUGARES, FILMES, FOTOS, PREMIOS, GIRLS_MATH, DESCONHECIDO;

    object Serializador : SerializadorDeEnum<ModuloDeEstiloDeVida>(
        entries,
        DESCONHECIDO,
        { it.name.lowercase().replace('_', '-') },
    )
}

@Serializable(with = OrigemDaAssinatura.Serializador::class)
enum class OrigemDaAssinatura {
    WEB, APP_STORE, PLAY_STORE, DESCONHECIDO;

    object Serializador : SerializadorDeEnum<OrigemDaAssinatura>(entries, DESCONHECIDO)
}

@Serializable(with = AcaoDeAuditoria.Serializador::class)
enum class AcaoDeAuditoria {
    CRIOU, EDITOU, APAGOU, IMPORTOU, PAGOU, MUDOU_PAPEL, REMOVEU, ENTROU, SAIU, CONVIDOU, DESCONHECIDO;

    object Serializador : SerializadorDeEnum<AcaoDeAuditoria>(entries, DESCONHECIDO)
}

/** UserResponse: o perfil de quem está logado (`/auth/me` e junto dos tokens). */
@Serializable
data class Usuario(
    val id: String,
    val email: String,
    val nome: String,
    val primeiroNome: String,
    val apelido: String? = null,
    /** A que o app mostra; nula mostra as iniciais. */
    val foto: String? = null,
    val fotoPropria: Boolean? = null,
    val temFotoDoGoogle: Boolean? = null,
    val role: PigguRole,
    val ativo: Boolean,
    val permissoes: JsonObject = JsonObject(emptyMap()),
    val preferencias: Preferencias? = null,
    val avisos: Avisos? = null,
    val plano: Plano,
    /** Até quando o Premium da família vale; nulo no gratuito. */
    val premiumAte: String? = null,
    val familia: String,
    /** Falso: o app pede o CPF. Ausente conta como informado. */
    val cpfInformado: Boolean? = null,
    /** Diferente da versão vigente, o app pede o aceite de novo. Ausente conta como aceita. */
    val termosAceitos: String? = null,
    /** Ausente conta como sem senha. */
    val temSenha: Boolean? = null,
    val emailConfirmado: Boolean? = null,
    /** ISO 3166; ausente conta como Brasil. */
    val pais: String? = null,
    /** aaaa-mm-dd; nula até a pessoa informar. */
    val nascimento: String? = null,
    /** Ausente conta como tudo desligado. */
    val compartilhamento: Compartilhamento? = null,
    val celularInformado: Boolean? = null,
    /** Falso: falta apelido, nascimento, CPF ou celular, e o app só abre "Sobre você" (D-088). */
    val cadastroCompleto: Boolean? = null,
)

/** Na resposta todos os campos podem faltar (identity.yaml); o PUT usa [PedidoDePreferencias]. */
@Serializable
data class Preferencias(
    val moeda: String? = null,
    /** Ponta "de" da cotação do topo; conta antiga não traz e vale a [moeda]. */
    val moedaCotacao: String? = null,
    val moedaConversao: String? = null,
    val mostrarCotacao: Boolean? = null,
    /** Fuso IANA; conta nova nasce em America/Sao_Paulo. */
    val fuso: String? = null,
    val idioma: String? = null,
    /** Nulo (conta antiga) liga todas: por isso não vira lista vazia. */
    val modulos: List<ModuloDeEstiloDeVida>? = null,
)

/** Avisos do dia por e-mail e no celular (resposta e corpo do PUT `/auth/me/alerts`). */
@Serializable
data class Avisos(val email: Boolean, val push: Boolean)

@Serializable
data class Compartilhamento(
    /** O parceiro vê tudo o que a pessoa lança, não só o compartilhado. */
    val mostraTudoAoParceiro: Boolean,
    /** Lançamentos novos já nascem compartilhados. */
    val compartilharPorPadrao: Boolean,
)

/** TokenResponse. No app o refresh vem no corpo, porque o login pede `refreshNoCorpo` (D-072). */
@Serializable
data class ParDeTokens(
    val accessToken: String,
    /** Segundos até o access vencer. */
    val expiresIn: Long,
    val refreshToken: String? = null,
    val usuario: Usuario,
)

/** ApiError: o corpo único de erro de todos os serviços (API_SPEC, Erros). */
@Serializable
data class CorpoDeErro(
    /** Já escrito para mostrar na tela. */
    val erro: String? = null,
    val codigo: String? = null,
    val campos: List<CampoInvalido> = emptyList(),
    val momento: String? = null,
)

@Serializable
data class CampoInvalido(val campo: String, val mensagem: String)

/** FamiliaResponse: a família de quem está logado; convites só chegam para o titular. */
@Serializable
data class Familia(
    val id: String,
    val nome: String,
    val plano: Plano,
    /** A assinatura da casa é o plano família. */
    val planoFamilia: Boolean,
    val membros: List<MembroDaFamilia> = emptyList(),
    val convites: List<ConviteDaFamilia> = emptyList(),
    val planoCasal: Boolean? = null,
)

@Serializable
data class MembroDaFamilia(
    val id: String,
    val nome: String,
    val email: String? = null,
    val foto: String? = null,
    val papel: PigguRole,
    val premium: Boolean,
    /** O titular convidou para parceiro e a pessoa ainda não respondeu. */
    val parceriaPendente: Boolean? = null,
)

@Serializable
data class ConviteDaFamilia(
    val id: String,
    val email: String,
    val venceEm: String,
    /** Nome da família que convidou. */
    val familia: String? = null,
)

/** Uma mudança nos dados da família (GET `/history` e `/family/history`). */
@Serializable
data class EventoDeAuditoria(
    val id: Long,
    /** O id zero é o próprio Piggu; nulo, conta excluída. */
    val autor: String? = null,
    val acao: AcaoDeAuditoria,
    val entidade: String,
    val entidadeId: String? = null,
    val antes: String? = null,
    val depois: String? = null,
    val quando: String,
)

/** Preços em reais, já formatados ("19,90"). */
@Serializable
data class Precos(val mensal: String, val anual: String)

/** PlanoResponse: o que vale agora e o que cada plano inclui. */
@Serializable
data class InfoDoPlano(
    val plano: Plano,
    val premiumAte: String? = null,
    val origem: OrigemDaAssinatura? = null,
    /** Até quando dá para desistir com o dinheiro de volta; nulo fora do prazo. */
    val reembolsoAte: String? = null,
    val diasDeTeste: Int,
    val assinaturaDisponivel: Boolean,
    val site: Precos,
    val app: Precos? = null,
    val gratuito: List<String> = emptyList(),
    val premium: List<String> = emptyList(),
    val planoFamilia: Boolean,
    /** Foi esta conta que assinou: só ela gerencia e pede reembolso. */
    val voceAssinou: Boolean,
    val familiaDisponivel: Boolean,
    val siteFamilia: Precos,
    val appFamilia: Precos,
    val pessoasNoFamilia: Int,
    val casaTemAssinatura: Boolean,
    val planoCasal: Boolean? = null,
    val casalDisponivel: Boolean? = null,
    val siteCasal: Precos? = null,
    val appCasal: Precos? = null,
)

/** Resposta de `/billing/checkout` e `/billing/portal`: a página do provedor de pagamento. */
@Serializable
data class EnderecoDePagamento(val url: String)

/** Falso: cancelou ainda no teste, sem nada a devolver. */
@Serializable
data class Reembolso(val reembolsado: Boolean)

// Corpos dos pedidos ao identity.

/** SenhaLoginRequest. O Android não tem cookie: o refresh sempre vem no corpo (D-072). */
@Serializable
data class PedidoDeLogin(
    val email: String,
    val senha: String,
    val lembrar: Boolean,
    val refreshNoCorpo: Boolean = true,
)

/** GoogleLoginRequest. `versaoDosTermos` só na conta nova, depois do TERMOS_NECESSARIOS. */
@Serializable
data class PedidoDoGoogle(
    val idToken: String,
    val versaoDosTermos: String? = null,
    val lembrar: Boolean,
    val refreshNoCorpo: Boolean = true,
)

/** CadastroRequest: responde 202 sem abrir sessão. */
@Serializable
data class PedidoDeCadastro(
    val nome: String,
    val email: String,
    val senha: String,
    /** ISO 3166, duas letras. */
    val pais: String,
    /** O CPF no Brasil; o documento de lá nos países que pedem um. */
    val documento: String? = null,
    val telefone: String,
    /** aaaa-mm-dd */
    val nascimento: String,
    val versaoDosTermos: String,
)

/** RefreshRequest, corpo de `/auth/refresh` e `/auth/logout`. */
@Serializable
data class PedidoDeRefresh(val refreshToken: String?)

/** DefinirSenhaRequest. O refresh vai junto para a sessão deste aparelho não cair. */
@Serializable
data class PedidoDeSenha(
    val senhaAtual: String? = null,
    val novaSenha: String,
    val refreshToken: String? = null,
)

/** ConfirmarEmailRequest. */
@Serializable
data class PedidoComToken(val token: String)

/** EsqueciSenhaRequest e ConviteRequest. */
@Serializable
data class PedidoDeEmail(val email: String)

/** RedefinirSenhaRequest. */
@Serializable
data class PedidoDeSenhaNova(val token: String, val novaSenha: String)

/** PreferencesRequest. */
@Serializable
data class PedidoDePreferencias(
    val moeda: String,
    val moedaCotacao: String? = null,
    val moedaConversao: String,
    val mostrarCotacao: Boolean,
    val fuso: String? = null,
)

/** CompartilhamentoRequest do identity: aqui é "mostrar", na resposta é "mostra". */
@Serializable
data class PedidoDeCompartilhamento(
    val mostrarTudoAoParceiro: Boolean,
    val compartilharPorPadrao: Boolean,
)

/** AparelhoRequest: o token do FCM deste celular, para o push dos avisos. */
@Serializable
data class PedidoDeAparelho(val token: String, val plataforma: String = "ANDROID")

/** ModulosRequest. */
@Serializable
data class PedidoDeModulos(val modulos: List<ModuloDeEstiloDeVida>)

/** PaisRequest: o documento só quando o país pede um (o CPF no Brasil). */
@Serializable
data class PedidoDePais(val pais: String, val documento: String? = null)

/** SobreVoceRequest: nascimento, CPF e celular só na primeira vez, quando faltam. */
@Serializable
data class PedidoSobreVoce(
    val apelido: String,
    val nascimento: String? = null,
    val cpf: String? = null,
    val telefone: String? = null,
)

/** TermosRequest. */
@Serializable
data class PedidoDeTermos(val versao: String)

/** FotoRequest: a foto de perfil já recortada e reduzida no aparelho. */
@Serializable
data class PedidoDeFoto(val imagemBase64: String, val mimeType: String = "image/jpeg")

/** UpdateUserRequest (só ADMIN). */
@Serializable
data class PedidoDeAlteracaoDeUsuario(
    val apelido: String? = null,
    val role: PigguRole? = null,
    val ativo: Boolean? = null,
)

/** NomeRequest, CategoryRequest e PlaceTagRequest. */
@Serializable
data class PedidoDeNome(val nome: String)

/** PapelRequest: o titular convida para parceiro ou volta a membro. */
@Serializable
data class PedidoDePapel(val papel: PigguRole)

/** CheckoutRequest. */
@Serializable
data class PedidoDeCheckout(val periodo: Periodo, val campanha: String? = null)

/** GooglePlayPurchaseRequest: a assinatura comprada no Google Play (`premium_mensal` ou `premium_anual`). */
@Serializable
data class PedidoDeCompraDoGooglePlay(val produto: String, val purchaseToken: String)

/** ErroDoApp: um erro que aconteceu no aparelho, já sem dado pessoal. */
@Serializable
data class ErroDoApp(
    val mensagem: String,
    val pilha: String,
    val tela: String,
    val versao: String,
)
