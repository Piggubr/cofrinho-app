import Foundation
import SwiftUI

// Tipos espelhando os DTOs do backend (frontend/src/app/core/api/models.ts).
//
// Os nomes seguem o que a API devolve, em portugues. Datas ficam como texto
// (LocalDate "aaaa-mm-dd", Instant ISO 8601), como no app web; ids sao UUIDs em texto.
// Valores monetarios sao Decimal, nunca Double.

/// ADMIN opera a instalacao; TITULAR e o dono da familia; MEMBRO foi convidado.
enum PigguRole: String, Codable, Sendable {
    case admin = "ADMIN"
    case titular = "TITULAR"
    case parceiro = "PARCEIRO"
    case membro = "MEMBRO"
}

enum Plano: String, Codable, Sendable {
    case gratuito = "GRATUITO"
    case premium = "PREMIUM"
}

/// Perfil devolvido por /auth/me e junto dos tokens. So os campos que o app usa por
/// enquanto; o resto do UserResponse e ignorado na decodificacao.
struct Usuario: Codable, Equatable, Sendable {
    let id: String
    let email: String
    let nome: String
    let primeiroNome: String
    let apelido: String?
    let foto: String?
    let role: PigguRole
    let ativo: Bool
    let plano: Plano
    let premiumAte: String?
    let familia: String
    let emailConfirmado: Bool?
    let pais: String?
    let nascimento: String?
    /// Falso: o Perfil oferece informar o CPF. Ausente conta como informado.
    var cpfInformado: Bool?
    /// Versao dos Termos aceita por ultimo; nula em conta antiga.
    var termosAceitos: String?
    var temSenha: Bool?
    /// A foto propria (enviada pelo app) e se ha a do Google para voltar a ela.
    var fotoPropria: Bool?
    var temFotoDoGoogle: Bool?
    /// Avisos do dia por e-mail e no celular.
    var avisos: Avisos?
    /// Moeda dos valores e a cotacao do topo; ausente em conta antiga.
    var preferencias: Preferencias?
    /// O que o casal ve um do outro e se o lancamento ja nasce compartilhado.
    var compartilhamento: Compartilhamento?

    /// Moeda em que os valores aparecem (ISO 4217).
    var moeda: String { preferencias?.moeda ?? "BRL" }

    /// Os Termos mudaram desde o ultimo aceite: o app so abre a tela de aceitar de novo.
    var termosPendentes: Bool { termosAceitos != Conta.versaoDosTermos }

    /// Falta como chamar ou a data de nascimento: o app pede os dois antes do resto.
    var sobreVocePendente: Bool {
        (apelido ?? "").trimmingCharacters(in: .whitespaces).isEmpty || (nascimento ?? "").isEmpty
    }

    /// Como o app chama a pessoa: o apelido, senao o primeiro nome.
    var comoChamar: String {
        if let apelido, !apelido.trimmingCharacters(in: .whitespaces).isEmpty {
            return apelido
        }
        return primeiroNome
    }
}

/// O refresh vem num cookie HttpOnly que so viaja para /api/auth, ou no corpo quando o login
/// pediu `refreshNoCorpo` (D-072).
struct ParDeTokens: Codable, Sendable {
    let accessToken: String
    let expiresIn: Int
    let usuario: Usuario
    var refreshToken: String?
}

/// Corpo unico de erro devolvido por todos os servicos.
struct CorpoDeErro: Codable, Sendable {
    struct Campo: Codable, Sendable {
        let campo: String
        let mensagem: String
    }

    let erro: String
    let codigo: String?
    let campos: [Campo]?
    let momento: String?
}

struct Gasto: Codable, Identifiable, Equatable, Sendable {
    let id: String
    /// aaaa-mm-dd
    let data: String
    let reciboId: String?
    let estabelecimento: String?
    let item: String
    let categoria: String
    let valor: Decimal
    let tipo: String?
    let origem: String?
    /// Id de quem lancou; nulo quando a conta foi excluida.
    let usuario: String?
    let registradoEm: String?
    let contaId: String?
    /// Parcela N de M; ausentes quando o gasto e a vista.
    let parcela: Int?
    let parcelas: Int?
    /// Compra em outra moeda: o valor acima ja e o convertido.
    let moedaOriginal: String?
    let valorOriginal: Decimal?
    let compartilhado: Bool?
}

struct ItemDeGasto: Codable, Equatable, Sendable {
    let item: String
    /// Nula: "Automática", o backend escolhe pelas regras.
    let categoria: String?
    let valor: Decimal
    /// "Fixo" (recorrente) ou "Variavel".
    var tipo: String?
    /// Compra em outra moeda: o `valor` ja vai convertido.
    var moedaOriginal: String?
    var valorOriginal: Decimal?
}

/// Corpo de POST /expenses. Campos nulos nao sao enviados: o backend aplica o padrao.
struct NovoLancamento: Codable, Equatable, Sendable {
    /// aaaa-mm-dd
    let data: String
    var estabelecimento: String?
    var origem: String?
    let itens: [ItemDeGasto]
    var contaId: String?
    var parcelas: Int?
    var dividirCom: [String]?
    var recorrente: Bool?
    var compartilhado: Bool?
    /// Sem resposta o servidor pode ter gravado: a fila reenvia com o mesmo id e ele nao duplica.
    var idDoEnvio: String?
}

/// Corpo de PUT /expenses/{id}. `compartilhado` ausente fica como esta (so quem lancou muda).
struct EdicaoDeGasto: Codable, Sendable {
    let item: String
    let categoria: String
    let valor: Decimal
    var compartilhado: Bool?
}

/// Na resposta todos os campos sao opcionais (docs/api/identity.yaml); no PUT vao moeda,
/// moedaConversao e mostrarCotacao.
struct Preferencias: Codable, Equatable, Sendable {
    var moeda: String?
    /// Ponta "de" da cotacao do topo; conta antiga nao traz e vale a `moeda`.
    var moedaCotacao: String?
    var moedaConversao: String?
    var mostrarCotacao: Bool?
    var fuso: String?
    var idioma: String?
    var modulos: [String]?
}

struct Avisos: Codable, Equatable, Sendable {
    let email: Bool
    let push: Bool
}

struct Compartilhamento: Codable, Equatable, Sendable {
    let mostraTudoAoParceiro: Bool
    let compartilharPorPadrao: Bool
}

/// Recorte do painel para quem mora com alguem.
enum Visao: String, CaseIterable, Sendable {
    case tudo, meu, casal

    var nome: LocalizedStringResource {
        switch self {
        case .tudo: "Tudo"
        case .meu: "Meu"
        case .casal: "Compartilhado"
        }
    }

    /// O backend so recebe o recorte quando nao e tudo.
    var parametro: String? { self == .tudo ? nil : rawValue }
}

struct Cotacao: Codable, Equatable, Sendable {
    let de: String
    let para: String
    let taxa: Decimal
    let data: String
    let fonte: String?
    let estimativa: Bool?
    let desatualizada: Bool?
}

struct Receita: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let data: String
    let descricao: String
    let categoria: String
    let valor: Decimal
    let usuario: String?
    let compartilhado: Bool?
}

/// Corpo de POST /incomes.
struct NovaReceita: Codable, Sendable {
    let data: String
    let descricao: String
    let categoria: String
    let valor: Decimal
    let compartilhado: Bool
    let recorrente: Bool
    /// So na recorrente em duas parcelas (D-087).
    var segundoDia: Int?
    var segundoValor: Decimal?
}

/// POST /bills.
struct NovaContaFixa: Codable, Sendable {
    let descricao: String
    let categoria: String
    let valor: Decimal
    let dia: Int
    let automatico: Bool
}

/// POST /accounts.
struct NovaConta: Codable, Sendable {
    let nome: String
    let tipo: ContaOuCartao.Tipo
    let fechamento: Int?
    let vencimento: Int?
    let compartilhado: Bool
}

/// O ano mes a mes e por categoria (Premium).
struct RelatorioDoAno: Codable, Equatable, Sendable {
    struct Mes: Codable, Equatable, Sendable, Identifiable {
        let mes: String
        let receitas: Decimal
        let gastos: Decimal
        let sobra: Decimal

        var id: String { mes }
    }

    struct Categoria: Codable, Equatable, Sendable, Identifiable {
        let categoria: String
        let total: Decimal

        var id: String { categoria }
    }

    let ano: Int
    let receitas: Decimal
    let gastos: Decimal
    let sobra: Decimal
    let meses: [Mes]
    let porCategoria: [Categoria]
}

/// Token de 30 minutos que abre o widget da Pluggy.
struct ConnectToken: Codable, Sendable {
    let accessToken: String
    let sandbox: Bool
}

/// Dinheiro que alguem deve a pessoa; so vira receita no "Recebi".
struct AReceber: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let quem: String
    let descricao: String?
    let valor: Decimal
    /// Data combinada, aaaa-mm-dd.
    let data: String
}

struct NovoAReceber: Codable, Sendable {
    let quem: String
    let descricao: String
    let valor: Decimal
    let data: String
}

/// Conta do mes (gasto) ou receita que entra todo mes, com a situacao no mes pedido.
struct ContaFixa: Codable, Identifiable, Equatable, Sendable {
    enum Situacao: String, Codable, Sendable {
        case paga = "PAGA", pendente = "PENDENTE", vencida = "VENCIDA"
    }

    let id: String
    let descricao: String
    let categoria: String
    let valor: Decimal
    let dia: Int
    let automatico: Bool
    let vencimento: String
    let situacao: Situacao
    let tipo: String?
    /// Segunda parcela da receita (o salario do dia 20 e do dia 5), D-087.
    let segundoDia: Int?
    let segundoValor: Decimal?
    let contaId: String?
    let usuario: String?
    let compartilhado: Bool?
}

struct Fatura: Codable, Equatable, Sendable {
    let mes: String
    /// Primeiro dia de compra desta fatura e a vespera do fechamento (a ultima que entra nela).
    let inicio: String?
    let ultimaCompra: String?
    let fechamento: String?
    let vencimento: String
    let total: Decimal
    /// Marcada como paga (ela ou uma seguinte): sai do "a pagar"; os gastos nao mudam (D-085).
    let paga: Bool?
}

/// GET /accounts/{id}/statement: a fatura do mes e os gastos dela.
struct FaturaComGastos: Codable, Sendable {
    let fatura: Fatura
    let gastos: [Gasto]
}

struct ContaOuCartao: Codable, Identifiable, Equatable, Sendable {
    enum Tipo: String, Codable, Sendable {
        case conta = "CONTA", cartao = "CARTAO"
    }

    let id: String
    let nome: String
    let tipo: Tipo
    let fechamento: Int?
    let vencimento: Int?
    let faturaAberta: Fatura?
    let faturaAPagar: Fatura?
    let usuario: String?
    let compartilhado: Bool?
}

struct Deposito: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let data: String
    let valor: Decimal
    let usuario: String?
    let objetivo: String?
}

struct Cofrinho: Codable, Equatable, Sendable {
    let depositos: [Deposito]
    let totalDepositos: Decimal
    let totalGastos: Decimal
    let saldo: Decimal
}

/// POST /piggy-bank/deposits; sem `objetivo` vai para o cofrinho geral.
struct NovoDeposito: Codable, Sendable {
    let valor: Decimal
    let data: String
    var objetivo: String?
}

struct ObjetivoDoCofrinho: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let nome: String
    let alvo: Decimal
    let prazo: String?
    let guardado: Decimal
    let falta: Decimal
    /// 0 a 100.
    let percentual: Double
    let porMes: Decimal?
    let concluido: Bool
    let atrasado: Bool
    let usuario: String?
    let compartilhado: Bool?
}

/// `compartilhado` ausente o backend deixa como esta.
struct PedidoDeObjetivo: Codable, Sendable {
    let nome: String
    let alvo: Decimal
    let prazo: String?
    var compartilhado: Bool?
}

struct Nota: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let titulo: String
    let texto: String?
    let data: String?
    /// Com valor, a nota lancou um gasto junto (`gastoId`); apagar a nota apaga o gasto.
    var valor: Decimal?
    var categoria: String?
    var gastoId: String?
    let criadoEm: String
}

struct NovaNota: Codable, Sendable {
    let titulo: String
    let texto: String?
    var data: String?
    var valor: Decimal?
    var categoria: String?
}

struct Orcamento: Codable, Identifiable, Equatable, Sendable {
    enum Alerta: String, Codable, Sendable {
        case ok = "OK", atencao = "ATENCAO", estourou = "ESTOUROU"
    }

    let id: String
    let categoria: String
    let limite: Decimal
    let gasto: Decimal
    /// Ja em porcentagem.
    let percentual: Decimal
    let alerta: Alerta
}

/// Minhas metas do mes (piggu-lifestyle): so de quem criou, e valem para o mes.
struct MetaDoMes: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let titulo: String
    var alvo: Int = 1
    var atual: Int = 0
    var mes: String?
    let concluida: Bool
}

struct NovaMetaDoMes: Codable, Sendable {
    let titulo: String
    let alvo: Int
    let atual: Int
}

/// Algo que a pessoa viu, quis e nao comprou. So ela ve.
struct ItemDoGirlsMath: Codable, Identifiable, Equatable, Sendable {
    let id: String
    var produto: String = ""
    var loja: String?
    let valor: Decimal
    let data: String
    var fotoAssetId: String?
    var temFoto: Bool?
}

struct NovoItemDoGirlsMath: Codable, Sendable {
    let produto: String
    let loja: String?
    let valor: Decimal
    let data: String
    let imageBase64: String?
    let mimeType: String?
}

struct MembroDaFamilia: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let nome: String
    var email: String?
    let papel: PigguRole
    var parceriaPendente: Bool?
}

struct ConviteDaFamilia: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let email: String
    let venceEm: String
    /// Nome da familia que convidou (nos convites recebidos).
    var familia: String?
}

struct Familia: Codable, Equatable, Sendable {
    var id: String?
    var nome: String = ""
    var plano: Plano?
    var planoFamilia: Bool?
    var planoCasal: Bool?
    let membros: [MembroDaFamilia]
    var convites: [ConviteDaFamilia]?
}

/// Quem lancou, alterou ou apagou cada coisa (GET /history e /family/history).
struct EventoDeAuditoria: Codable, Identifiable, Equatable, Sendable {
    let id: Int
    let autor: String?
    let acao: String
    let entidade: String
    let antes: String?
    let depois: String?
    let quando: String
}

struct MovimentoDeMoedas: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let data: String
    let valor: Int
    let motivo: String
}

/// Fofocoins: o saldo e o que entrou e saiu.
struct SaldoDeMoedas: Codable, Equatable, Sendable {
    let saldo: Int
    let historico: [MovimentoDeMoedas]
}

struct Premio: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let nome: String
    let descricao: String?
    let preco: Int
    let ativo: Bool
}

struct NovoPremio: Codable, Sendable {
    let nome: String
    let descricao: String
    let preco: Int
    let ativo: Bool
}

struct Resgate: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let premio: String
    let preco: Int
    let usuario: String?
    let status: String
    let data: String
    let saldo: Int
}

struct MoedaDisponivel: Codable, Equatable, Sendable, Identifiable {
    let codigo: String
    let nome: String

    var id: String { codigo }
}

struct RegraDeCategoria: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let termo: String
    let categoria: String
}

struct AcertoDeDivisao: Codable, Equatable, Sendable {
    let pessoa: String?
    let pagou: Decimal
    let parte: Decimal
    let saldo: Decimal
}

struct TransferenciaDoAcerto: Codable, Equatable, Sendable {
    let de: String?
    let para: String?
    let valor: Decimal
}

struct ConferenciaDePreco: Codable, Equatable, Sendable {
    let media: Decimal
    let percentual: Decimal
    let acima: Bool
}

/// Conta lida pelo Open Finance (piggu-banking).
struct ContaBancaria: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let conexaoId: String
    let instituicao: String
    let nome: String
    let numero: String?
    let saldo: Decimal
    let moeda: String
}

struct SituacaoDoOpenFinance: Codable, Sendable {
    let habilitado: Bool
}

/// Card do mes: receitas - gastos = sobra; sobra / receitas = taxa de poupanca.
struct ResumoDoMes: Codable, Equatable, Sendable {
    struct PorCategoria: Codable, Equatable, Sendable, Identifiable {
        let categoria: String
        let total: Decimal
        let anterior: Decimal

        var id: String { categoria }
    }

    /// aaaa-mm
    let mes: String
    let receitas: Decimal
    let gastos: Decimal
    let sobra: Decimal
    /// Em porcentagem; nula quando o mes nao tem receita.
    let taxaDePoupanca: Decimal?
    let gastosMesAnterior: Decimal
    /// Gastos contra o mes anterior, em porcentagem; nula se o anterior nao teve gasto.
    let variacao: Decimal?
    /// So no mes corrente: o ritmo de gasto ate hoje levado ao fim do mes.
    let projecaoDeGastos: Decimal?
    let porCategoria: [PorCategoria]
}

/// Resposta de GET /categories: as oito de fabrica mais as criadas.
struct Categorias: Codable, Equatable, Sendable {
    let categorias: [String]
}

struct PedidoDeLogin: Codable, Sendable {
    let email: String
    let senha: String
    let lembrar: Bool
    /// So o app com o Keychain ligado pede; ausente, o refresh vem no cookie.
    var refreshNoCorpo: Bool?
}

/// Corpo de /auth/refresh e /auth/logout (RefreshRequest). Sem refresh, vai `{}` e vale o cookie.
struct PedidoDeRefresh: Codable, Sendable {
    let refreshToken: String?
}

// MARK: - Entrada e conta (auth.service.ts e users.service.ts)

/// POST /auth/google. `versaoDosTermos` so na conta nova, depois do TERMOS_NECESSARIOS.
struct PedidoDoGoogle: Codable, Sendable {
    let idToken: String
    var versaoDosTermos: String?
    let lembrar: Bool
    var refreshNoCorpo: Bool?
}

/// POST /auth/apple (AppleLoginRequest): o nonce vai sem hash; `nome` so vem na primeira vez.
struct PedidoDaApple: Codable, Sendable {
    let idToken: String
    let nonce: String
    var nome: String?
    var versaoDosTermos: String?
    let lembrar: Bool
    var refreshNoCorpo: Bool?
}

/// POST /auth/cadastro (CadastroRequest). Responde 202 sem abrir sessao.
struct PedidoDeCadastro: Codable, Equatable, Sendable {
    let nome: String
    let email: String
    let senha: String
    /// ISO 3166, duas letras.
    let pais: String
    /// O CPF so com os digitos.
    let documento: String
    /// "+55 11 99999-8888"
    let telefone: String
    /// aaaa-mm-dd
    let nascimento: String
    var versaoDosTermos = Conta.versaoDosTermos
}

/// Corpo de /auth/confirmar-email.
struct PedidoComToken: Codable, Sendable {
    let token: String
}

struct PedidoDeEmail: Codable, Sendable {
    let email: String
}

struct PedidoDeSenhaNova: Codable, Sendable {
    let token: String
    let novaSenha: String
}

/// PUT /me/pais
struct PedidoDePais: Codable, Sendable {
    let pais: String
    let documento: String?
}

/// PUT /me/sobre-voce: a data so na primeira vez.
struct PedidoSobreVoce: Codable, Sendable {
    let apelido: String
    let nascimento: String?
}

/// PUT /me/termos
struct PedidoDeTermos: Codable, Sendable {
    let versao: String
}

// MARK: - Estilo de vida (compras, lugares, filmes e fotos)

struct ItemDeCompra: Codable, Identifiable, Equatable, Sendable {
    enum Lista: String, Codable, CaseIterable, Sendable {
        case compras = "Compras", desejos = "Desejos"
    }

    let id: String
    let item: String
    let quantidade: String?
    let lista: Lista
    var comprado: Bool
    let marca: String?
    /// So HTTPS: o backend descarta outro esquema.
    let imagem: String?
    let codigo: String?
}

struct NovoItemDeCompra: Codable, Sendable {
    let item: String
    let quantidade: String
    let lista: ItemDeCompra.Lista
    var marca: String?
    var imagem: String?
    var codigo: String?
}

/// Produto do catalogo aberto (Open Food Facts): marca, imagem e codigo sem digitar.
struct ProdutoDoCatalogo: Codable, Identifiable, Equatable, Sendable {
    let codigo: String
    let nome: String
    let marca: String?
    let quantidade: String?
    let imagem: String?

    var id: String { codigo }
}

struct Lugar: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let nome: String
    let categoria: String?
    let localizacao: String?
    let nota: Int
    let comentario: String?
    let data: String
    let marcacoes: [String]
    let fotoAssetId: String?
    let temFoto: Bool?
    let valor: Decimal
    let usuario: String?
}

struct NovoLugar: Codable, Sendable {
    let nome: String
    let categoria: String?
    let localizacao: String?
    let nota: Int
    let comentario: String?
    let data: String
    let marcacoes: [String]
    let valor: Decimal
    let imageBase64: String?
    let mimeType: String?
}

/// Filme da lista do casal; cada pessoa tem a propria nota de 1 a 5.
struct Filme: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let tmdbId: String
    let titulo: String
    let ano: String?
    let poster: String?
    let nota: Double?
    let sinopse: String?
    let assistido: Bool
    /// Id da pessoa para a nota dela.
    let avaliacoes: [String: Int]
    let usuario: String?
}

struct FilmeDoCatalogo: Codable, Identifiable, Equatable, Sendable {
    let tmdbId: String
    let titulo: String
    let ano: String?
    let poster: String?
    let nota: Double?
    let sinopse: String?

    var id: String { tmdbId }
}

/// Foto do feed do mes, guardada no piggu-media.
struct FotoDoFeed: Codable, Identifiable, Equatable, Sendable {
    let id: String
    let mesKey: String
    let assetId: String
    let legenda: String?
    let usuario: String?
    let criadoEm: String
}
