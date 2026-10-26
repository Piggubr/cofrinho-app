import Foundation

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
    let categoria: String?
    let valor: Decimal
}

/// Corpo de POST /expenses. Campos nulos nao sao enviados: o backend aplica o padrao.
struct NovoLancamento: Codable, Equatable, Sendable {
    /// aaaa-mm-dd
    let data: String
    let estabelecimento: String?
    let itens: [ItemDeGasto]
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
