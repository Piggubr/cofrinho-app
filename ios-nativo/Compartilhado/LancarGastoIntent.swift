import AppIntents
import Foundation
import Observation

/// "Lancar gasto no Piggu" pela Siri, pelo app Atalhos, pela busca do iPhone e pelo controle da
/// Central de Controle (vindo de frontend/ios/App/App/LancarGastoIntent.swift). Abre o app na
/// folha de lancar; se a pessoa disser o valor, ele ja vem preenchido. O gasto so e gravado
/// quando ela confirma, como qualquer lancamento.
struct LancarGastoIntent: AppIntent {
    static let title: LocalizedStringResource = "Lançar gasto"
    static let description = IntentDescription("Abre o Piggu pronto para lançar um gasto.")
    static let openAppWhenRun = true

    @Parameter(title: "Valor")
    var valor: Double?

    init() {}

    init(valor: Double?) {
        self.valor = valor
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        CentralDeRotas.compartilhada.abrir(Self.link(valor: valor))
        return .result()
    }

    /// piggu://gasto?valor=12.50, o mesmo link do atalho do icone.
    static func link(valor: Double?) -> URL {
        guard let valor, valor > 0 else { return URL(string: "piggu://gasto")! }
        let texto = String(format: "%.2f", locale: Locale(identifier: "en_US_POSIX"), valor)
        return URL(string: "piggu://gasto?valor=\(texto)")!
    }
}

/// Links pedidos pelo sistema (Siri, Atalhos, controles, notificacoes, Spotlight) para a tela abrir.
@MainActor
@Observable
final class CentralDeRotas {
    static let compartilhada = CentralDeRotas()

    private(set) var pendente: URL?

    func abrir(_ link: URL) {
        pendente = link
    }

    /// A tela pega o link e limpa, para nao abrir duas vezes.
    func consumir() -> URL? {
        defer { pendente = nil }
        return pendente
    }
}
