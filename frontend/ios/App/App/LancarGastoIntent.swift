import AppIntents

/**
 * "Lancar gasto no Piggu" pela Siri, pelo app Atalhos ou pela busca do iPhone.
 *
 * Abre o app no lancamento rapido; se a pessoa disser o valor, ele ja vem preenchido.
 * O gasto so e gravado quando ela confirma na tela, como qualquer lancamento.
 */
@available(iOS 16.0, *)
struct LancarGastoIntent: AppIntent {
    static var title: LocalizedStringResource = "Lançar gasto"
    static var description = IntentDescription("Abre o Piggu pronto para lançar um gasto.")
    static var openAppWhenRun: Bool = true

    @Parameter(title: "Valor")
    var valor: Double?

    @MainActor
    func perform() async throws -> some IntentResult {
        var rota = AtalhosPlugin.rotaDeGasto
        if let valor, valor > 0 {
            rota += "&valor=" + String(format: "%.2f", locale: Locale(identifier: "en_US_POSIX"), valor)
        }
        AtalhosPlugin.abrir(rota)
        return .result()
    }
}

/** As frases que a Siri reconhece sem configurar nada. */
@available(iOS 16.0, *)
struct AtalhosDoPiggu: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: LancarGastoIntent(),
            phrases: [
                "Lançar gasto no \(.applicationName)",
                "Novo gasto no \(.applicationName)",
                "Anotar gasto no \(.applicationName)",
            ],
            shortTitle: "Lançar gasto",
            systemImageName: "plus.circle"
        )
    }
}
