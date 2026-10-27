import Foundation

/// Aba do app logado. O valor e o caminho da rota do site.
enum Aba: String, CaseIterable, Sendable {
    case painel, gastos, receitas, relatorios, mais
}

/// Para onde um link leva: `piggu://...` (atalhos, Siri, widgets) ou um endereco do site.
///
/// Aceita os mesmos links do app Capacitor (frontend/ios/App/App/AtalhosPlugin.swift), que
/// ja estao em atalhos salvos: `piggu://gastos?rapido=1&valor=12.50`. E o curto
/// `piggu://gasto?valor=12.50`. As rotas do site que ainda nao existem no app sao ignoradas.
enum Rota: Hashable, Identifiable, Sendable {
    case aba(Aba)
    case lancarGasto(valor: Decimal?)
    /// Os links dos e-mails (https://piggu.com.br/confirmar-email?token=...), por Universal
    /// Link; abrem por cima de qualquer tela, com ou sem sessao.
    case confirmarEmail(token: String)
    case redefinirSenha(token: String)
    case textoLegal(TextoLegalView.Documento)
    case esqueciSenha
    /// Informar o CPF; so com sessao.
    case cpf

    var id: Self { self }

    /// Abre numa folha por cima do app, logado ou nao.
    var abreFolha: Bool {
        switch self {
        case .confirmarEmail, .redefinirSenha, .textoLegal, .esqueciSenha, .cpf: true
        case .aba, .lancarGasto: false
        }
    }

    /// O mesmo teto do backend (ExpenseItemRequest).
    static let valorMaximo: Decimal = 1_000_000

    private static let dominios: Set<String> = ["piggu.com.br", "www.piggu.com.br"]

    init?(url: URL) {
        guard let componentes = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        var caminho = componentes.path.split(separator: "/").map(String.init)
        switch componentes.scheme?.lowercased() {
        case "piggu":
            // piggu://gastos/x: o host e o primeiro pedaco do caminho.
            caminho.insert(componentes.host ?? "", at: 0)
        case "https" where Self.dominios.contains(componentes.host?.lowercased() ?? ""):
            break
        default:
            return nil
        }
        let consulta = Dictionary(
            (componentes.queryItems ?? []).map { ($0.name, $0.value ?? "") }, uniquingKeysWith: { $1 })

        switch caminho.first(where: { !$0.isEmpty }) ?? "painel" {
        case "gasto":
            self = .lancarGasto(valor: Self.valor(consulta["valor"]))
        case "gastos" where consulta["rapido"] == "1":
            self = .lancarGasto(valor: Self.valor(consulta["valor"]))
        case "painel":
            self = .aba(.painel)
        case "gastos":
            self = .aba(.gastos)
        case "receitas":
            self = .aba(.receitas)
        case "relatorios":
            self = .aba(.relatorios)
        case "mais", "perfil", "plano":
            self = .aba(.mais)
        case "confirmar-email":
            self = .confirmarEmail(token: consulta["token"] ?? "")
        case "redefinir-senha":
            self = .redefinirSenha(token: consulta["token"] ?? "")
        case "esqueci-senha":
            self = .esqueciSenha
        case "cpf":
            self = .cpf
        case "termos":
            self = .textoLegal(.termos)
        case "privacidade":
            self = .textoLegal(.privacidade)
        default:
            return nil
        }
    }

    /// Valor fora do intervalo ou ilegivel abre o lancamento em branco.
    private static func valor(_ texto: String?) -> Decimal? {
        guard let texto, let valor = Formatos.lerValor(texto), valor > 0, valor <= valorMaximo else {
            return nil
        }
        return valor
    }
}
