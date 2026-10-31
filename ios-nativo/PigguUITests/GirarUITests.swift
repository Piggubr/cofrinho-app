import XCTest

/// Deitar e voltar o aparelho (a troca de tamanho do iPhone Duo ao abrir e fechar) nao pode
/// perder a folha aberta, o que foi digitado nem a tela aberta no Mais (D-141).
final class GirarUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    override func tearDown() {
        XCUIDevice.shared.orientation = .portrait
    }

    @MainActor
    private func abrirApp() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-PigguDemo", "YES", "-PigguDemoLogado", "YES"]
        app.launch()
        return app
    }

    @MainActor
    private func deitarEVoltar(_ app: XCUIApplication) throws {
        XCUIDevice.shared.orientation = .landscapeLeft
        let janela = app.windows.firstMatch
        let deitou = NSPredicate { _, _ in janela.frame.width > janela.frame.height }
        let espera = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: deitou, object: nil)], timeout: 5)
        // O simulador do iPhone Duo (Xcode 27.1) nao gira: ali o teste nao diz nada.
        if espera != .completed { throw XCTSkip("Este simulador não deita a janela") }
        XCUIDevice.shared.orientation = .portrait
    }

    @MainActor
    func testFolhaEValorFicamAoGirar() throws {
        let app = abrirApp()
        app.open(URL(string: "piggu://gasto")!)
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
        app.typeText("12,50")
        let valor = app.textFields.matching(NSPredicate(format: "value CONTAINS '12,50'")).firstMatch
        XCTAssertTrue(valor.waitForExistence(timeout: 3))
        try deitarEVoltar(app)
        XCTAssertTrue(app.navigationBars["Lançar gasto"].waitForExistence(timeout: 3))
        XCTAssertTrue(valor.exists, "O valor digitado sumiu ao girar")
    }

    @MainActor
    func testTelaDoMaisFicaAoGirar() throws {
        let app = abrirApp()
        app.open(URL(string: "piggu://orcamentos")!)
        XCTAssertTrue(app.navigationBars["Orçamento"].waitForExistence(timeout: 8))
        try deitarEVoltar(app)
        XCTAssertTrue(app.navigationBars["Orçamento"].waitForExistence(timeout: 3))
    }
}
