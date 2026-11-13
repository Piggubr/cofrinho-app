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
        }
    }
}
