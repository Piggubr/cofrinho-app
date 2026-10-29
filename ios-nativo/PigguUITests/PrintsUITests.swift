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
            try XCUIScreen.main.screenshot().pngRepresentation
                .write(to: pasta.appendingPathComponent("\(tela).png"))
        }
    }
}
