import Foundation

/// O que os widgets e a Siri mostram, gravado pelo app no App Group a cada abertura e depois de
/// lancar um gasto. So numeros do mes: nunca token, e-mail ou lista de gastos.
struct ResumoDoWidget: Codable, Equatable, Sendable {
    static let grupo = "group.br.com.piggu.app"
    static let chave = "resumo-do-widget"

    struct Meta: Codable, Equatable, Sendable {
        let titulo: String
        let atual: Int
        let alvo: Int
    }

    /// aaaa-mm
    let mes: String
    let gasto: Decimal
    /// Limite total do mes (Orcamento); nil sem limite.
    let limite: Decimal?
    /// ISO 4217, a moeda da pessoa.
    let moeda: String
    /// A primeira meta do mes ainda aberta (ou a ultima, se todas acabaram).
    let meta: Meta?
    let metasConcluidas: Int
    let metasTotal: Int
    /// A cor do Piggu escolhida no app (Tema.rawValue).
    let tema: String
    let atualizadoEm: Date

    /// Quanto do limite ja foi, de 0 a 1 (pode passar de 1); nil sem limite.
    var fracaoDoLimite: Double? {
        guard let limite, limite > 0 else { return nil }
        return NSDecimalNumber(decimal: gasto / limite).doubleValue
    }

    var restante: Decimal? { limite.map { $0 - gasto } }

    func moeda(_ valor: Decimal) -> String {
        valor.formatted(.currency(code: moeda).locale(Locale(identifier: "pt_BR")))
    }

    static var ajustes: UserDefaults? { UserDefaults(suiteName: grupo) }

    static func ler(_ ajustes: UserDefaults? = ajustes) -> ResumoDoWidget? {
        guard let dados = ajustes?.data(forKey: chave) else { return nil }
        return try? JSONDecoder().decode(ResumoDoWidget.self, from: dados)
    }

    func gravar(_ ajustes: UserDefaults? = ResumoDoWidget.ajustes) {
        ajustes?.set(try? JSONEncoder().encode(self), forKey: Self.chave)
    }

    /// Ao sair da conta: os numeros sao da pessoa.
    static func apagar(_ ajustes: UserDefaults? = ajustes) {
        ajustes?.removeObject(forKey: chave)
    }

    /// Para a galeria de widgets e o preview.
    static let exemplo = ResumoDoWidget(
        mes: "2026-10", gasto: 1039.39, limite: 2500, moeda: "BRL",
        meta: Meta(titulo: "Caminhar 10 km", atual: 6, alvo: 10), metasConcluidas: 1, metasTotal: 3,
        tema: "chiclete", atualizadoEm: Date())
}
