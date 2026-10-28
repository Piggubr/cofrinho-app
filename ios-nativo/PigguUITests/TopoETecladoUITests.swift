import XCTest

/// O teclado fecha ao tocar fora, o + lanca gasto ou receita, e a busca e o assistente do topo abrem as telas.
final class TopoETecladoUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    @MainActor
    private func abrirApp(_ extras: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-PigguDemo", "YES", "-PigguDemoLogado", "YES"] + extras
        app.launch()
        return app
    }

    /// Regressao de 27/10/2026: no iPhone, o teclado do valor ficou aberto depois de bloquear e
    /// desbloquear, sem jeito de fechar.
    @MainActor
    func testTocarForaFechaOTeclado() {
        let app = abrirApp()
        app.open(URL(string: "piggu://gasto")!)
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
        app.navigationBars["Lançar gasto"].staticTexts["Lançar gasto"].tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForNonExistence(timeout: 3))
    }

    /// O + abre as opcoes, como no site: a receita sai de qualquer aba.
    @MainActor
    func testMaisLancaReceita() {
        let app = abrirApp()
        app.buttons["Lançar"].firstMatch.tap()
        app.buttons["Lançar receita"].tap()
        XCTAssertTrue(app.navigationBars["Nova receita"].waitForExistence(timeout: 5))
    }

    @MainActor
    func testBuscaAbreOGasto() {
        let app = abrirApp()
        app.buttons["Buscar"].firstMatch.tap()
        let campo = app.searchFields.firstMatch
        XCTAssertTrue(campo.waitForExistence(timeout: 3))
        campo.typeText("mercado")
        let achado = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Mercado da semana'")).firstMatch
        XCTAssertTrue(achado.waitForExistence(timeout: 5))
        achado.tap()
        XCTAssertTrue(app.navigationBars["Editar gasto"].waitForExistence(timeout: 5))
    }

    @MainActor
    func testAssistentePedeAutorizacaoEResponde() {
        let app = abrirApp(["-PigguDemoConta", "premium"])
        app.buttons["Perguntar ao assistente"].firstMatch.tap()
        let autorizo = app.buttons["Autorizo e quero perguntar"]
        XCTAssertTrue(autorizo.waitForExistence(timeout: 5))
        autorizo.tap()
        app.buttons["Quanto gastei este mês?"].tap()
        let abrir = app.buttons["Abrir Relatórios"]
        XCTAssertTrue(abrir.waitForExistence(timeout: 5))
        abrir.tap()
        XCTAssertTrue(app.navigationBars["Relatórios"].waitForExistence(timeout: 5))
    }
}
