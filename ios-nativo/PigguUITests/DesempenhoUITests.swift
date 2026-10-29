import XCTest

/// Abrir o app ate o Painel com dados, com o cache cheio (meta: menos de 1 s). Mede o
/// intervalo "Abrir ate o Painel" do os_signpost (Desempenho.swift), na demonstracao.
final class DesempenhoUITests: XCTestCase {
    @MainActor
    func testAbrirAtePainelComCache() {
        medirAbertura(argumentos: [])
    }

    @MainActor
    func testAbrirAtePainelComMilGastos() {
        medirAbertura(argumentos: ["-PigguDemoMuitos", "3000"])
    }

    /// Rolar os Gastos e o Painel de um mes com 3000 gastos: o Instruments mede os engasgos.
    @MainActor
    func testRolarGastosComMilGastos() {
        medirRolagem(aba: "gastos")
    }

    @MainActor
    func testRolarPainelComMilGastos() {
        medirRolagem(aba: "painel")
    }

    @MainActor
    private func medirRolagem(aba: String) {
        let app = XCUIApplication()
        app.launchArguments = ["-PigguDemo", "YES", "-PigguDemoLogado", "YES", "-PigguDemoMuitos", "3000",
                               "-PigguAba", aba]
        app.launch()
        let lista = app.collectionViews.firstMatch.exists ? app.collectionViews.firstMatch : app.scrollViews.firstMatch
        XCTAssertTrue(lista.waitForExistence(timeout: 15))
        Thread.sleep(forTimeInterval: 2)
        let opcoes = XCTMeasureOptions()
        opcoes.invocationOptions = [.manuallyStop]
        opcoes.iterationCount = 3
        measure(metrics: [XCTOSSignpostMetric.scrollingAndDecelerationMetric], options: opcoes) {
            lista.swipeUp(velocity: .fast)
            stopMeasuring()
            lista.swipeDown(velocity: .fast)
        }
    }

    @MainActor
    private func medirAbertura(argumentos: [String]) {
        let app = XCUIApplication()
        app.launchArguments = ["-PigguDemo", "YES", "-PigguDemoLogado", "YES", "-PigguDemoCache", "YES"] + argumentos
        let resumo = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS 'Sobra do mês'")).firstMatch
        // A primeira abertura enche o cache.
        app.launch()
        XCTAssertTrue(resumo.waitForExistence(timeout: 15))
        Thread.sleep(forTimeInterval: 2)

        let opcoes = XCTMeasureOptions()
        opcoes.iterationCount = 5
        let abertura = XCTOSSignpostMetric(subsystem: "br.com.piggu.app", category: "PointsOfInterest", name: "Abrir ate o Painel")
        measure(metrics: [abertura, XCTApplicationLaunchMetric(waitUntilResponsive: true)], options: opcoes) {
            app.terminate()
            app.launch()
            XCTAssertTrue(resumo.waitForExistence(timeout: 15))
        }
    }
}
