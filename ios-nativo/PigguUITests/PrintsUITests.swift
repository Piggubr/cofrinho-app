import StoreKitTest
import XCTest

/// Print de cada tela da demonstracao numa pasta do Mac, para revisar texto grande, escuro e
/// iPad. So roda com a pasta: `TEST_RUNNER_PIGGU_PRINTS=/caminho xcodebuild test ...`, e
/// opcionalmente `TEST_RUNNER_PIGGU_TAMANHO` (UICTContentSizeCategory...) e
/// `TEST_RUNNER_PIGGU_ESCURO=1` e `TEST_RUNNER_PIGGU_PAISAGEM=1`.
final class PrintsUITests: XCTestCase {
    @MainActor
    func testPrintsDasTelas() throws {
        let ambiente = ProcessInfo.processInfo.environment
        let pasta = try XCTUnwrap(ambiente["PIGGU_PRINTS"].map { URL(fileURLWithPath: $0) },
                                  "Sem TEST_RUNNER_PIGGU_PRINTS")
        try FileManager.default.createDirectory(at: pasta, withIntermediateDirectories: true)
        XCUIDevice.shared.appearance = ambiente["PIGGU_ESCURO"] == "1" ? .dark : .light
        XCUIDevice.shared.orientation = ambiente["PIGGU_PAISAGEM"] == "1" ? .landscapeLeft : .portrait
        defer { XCUIDevice.shared.appearance = .light }

        // Os planos do Piggu.storekit (em reais), nao os do sandbox da conta do simulador.
        let loja = try SKTestSession(contentsOf: URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("PigguTests/Piggu.storekit"))
        loja.resetToDefaultState()
        loja.disableDialogs = true

        let app = XCUIApplication()
        app.launchArguments = ["-PigguDemo", "YES", "-PigguDemoLogado", "YES",
                               "-UIPreferredContentSizeCategoryName",
                               ambiente["PIGGU_TAMANHO"] ?? "UICTContentSizeCategoryL"]
            + (ambiente["PIGGU_ARGUMENTOS"]?.split(separator: " ").map(String.init) ?? [])
        app.launch()
        let escolhidas = ambiente["PIGGU_TELAS"]?.split(separator: " ").map(String.init) ?? []
        let telas = escolhidas.isEmpty ? AcessibilidadeUITests.telas : escolhidas
        for tela in telas {
            app.open(URL(string: "piggu://\(tela)")!)
            Thread.sleep(forTimeInterval: 1.5)
            // O link reabre o app: com a maquina ocupada, a tela demora a sair do "Carregando…".
            _ = app.descendants(matching: .any)["Carregando…"].waitForNonExistence(timeout: 8)
            // O Premium espera a loja da Apple trazer os planos.
            if tela == "plano" { _ = app.staticTexts["Escolha o plano abaixo."].waitForExistence(timeout: 30); Thread.sleep(forTimeInterval: 2) }
            try XCUIScreen.main.screenshot().pngRepresentation
                .write(to: pasta.appendingPathComponent("\(tela).png"))
            // A revisao da Apple pede um print de cada produto: escolhe um por um.
            if tela == "plano" {
                for (arquivo, nome) in [("individual-mensal", "Individual Mensal"), ("individual-anual", "Individual Anual"),
                                        ("casal-mensal", "Casal Mensal"), ("casal-anual", "Casal Anual"),
                                        ("familia-mensal", "Família Mensal"), ("familia-anual", "Família Anual")] {
                    let cartao = app.staticTexts[nome]
                    let janela = app.windows.firstMatch
                    // O fim da lista fica sob o botao de assinar: traz o cartao para o meio da tela.
                    for _ in 0..<6 where cartao.exists {
                        let meio = cartao.frame.midY / janela.frame.height
                        if (0.3...0.62).contains(meio) { break }
                        let fim = min(max(0.5 - (meio - 0.45), 0.1), 0.9)
                        janela.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
                            .press(forDuration: 0.1, thenDragTo: janela.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: fim)))
                        Thread.sleep(forTimeInterval: 0.8)
                    }
                    cartao.tap()
                    Thread.sleep(forTimeInterval: 1.5)
                    try XCUIScreen.main.screenshot().pngRepresentation
                        .write(to: pasta.appendingPathComponent("plano-\(arquivo).png"))
                }
            }
        }
    }
}
