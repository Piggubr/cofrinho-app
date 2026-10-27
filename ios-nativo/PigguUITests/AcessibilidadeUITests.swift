import XCTest

/// Auditoria de acessibilidade de cada tela (o mesmo motor do Accessibility Inspector) na
/// demonstracao, no tamanho de texto padrao, no maior e no escuro. Roda no esquema PigguUITests:
/// `xcodebuild test -scheme PigguUITests -destination ...`.
///
/// E um relatorio, nao uma trava: a auditoria tambem aponta o que nao e problema (texto sob a
/// barra de abas de vidro, o link "Legal" do mapa, valor em R$ como "rotulo ilegivel", botao
/// desligado). O relatorio vai no log e anexado ao resultado do teste; leia antes de cada versao.
final class AcessibilidadeUITests: XCTestCase {
    /// As rotas de app.routes.ts que abrem uma tela logada (Rota.swift).
    static let telas = [
        "painel", "gastos", "receitas", "relatorios", "orcamentos", "contas-fixas", "contas-e-cartoes",
        "calendario", "planejar", "metas", "girls-math", "compras", "lugares", "filmes", "feed",
        "premios", "familia", "perfil", "plano", "mais",
    ]

    override func setUp() {
        continueAfterFailure = true
    }

    @MainActor
    func testTelasNoTamanhoPadrao() throws {
        try auditar(tamanho: "UICTContentSizeCategoryL")
    }

    @MainActor
    func testTelasNoMaiorTamanho() throws {
        try auditar(tamanho: "UICTContentSizeCategoryAccessibilityXXXL")
    }

    @MainActor
    func testTelasNoEscuro() throws {
        XCUIDevice.shared.appearance = .dark
        defer { XCUIDevice.shared.appearance = .light }
        try auditar(tamanho: "UICTContentSizeCategoryL")
    }

    @MainActor
    private func auditar(tamanho: String) throws {
        let app = XCUIApplication()
        app.launchArguments = ["-PigguDemo", "YES", "-PigguDemoLogado", "YES",
                               "-UIPreferredContentSizeCategoryName", tamanho]
        app.launch()
        var problemas: [String] = []
        for tela in Self.telas {
            app.open(URL(string: "piggu://\(tela)")!)
            // A tela carrega da demonstracao na hora; espera a animacao da navegacao.
            _ = app.navigationBars.firstMatch.waitForExistence(timeout: 3)
            Thread.sleep(forTimeInterval: 0.8)
            try app.performAccessibilityAudit { problema in
                let elemento = problema.element.map { "\($0.elementType.rawValue) '\($0.label)' \($0.identifier)" } ?? "-"
                problemas.append("[\(tela)] \(problema.auditType): \(problema.compactDescription) | \(elemento)")
                return true
            }
        }
        let relatorio = problemas.joined(separator: "\n")
        print("AUDITORIA \(tamanho)\n\(relatorio)\nFIM DA AUDITORIA")
        let anexo = XCTAttachment(string: relatorio)
        anexo.name = "Auditoria \(tamanho)"
        anexo.lifetime = .keepAlways
        add(anexo)
    }
}
