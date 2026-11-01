import AppIntents
import Foundation

/// "Quanto gastei este mes no Piggu": responde com a fala, sem abrir o app. Pergunta a API com a
/// sessao deste aparelho (o refresh no cookie ou no Keychain); sem rede ou sem sessao, responde
/// com o ultimo resumo que o app guardou, dizendo de quando e.
struct QuantoGasteiIntent: AppIntent {
    static let title: LocalizedStringResource = "Quanto gastei este mês"
    static let description = IntentDescription("Diz quanto você gastou no mês e quanto ainda cabe no limite.")
    static let openAppWhenRun = false
    // Os numeros sao da pessoa: com o iPhone bloqueado, a Siri pede o desbloqueio antes (D-162).
    static let authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        // Com a trava do Piggu ligada, so o app mostra os gastos, depois do Face ID (D-162).
        if UserDefaults.standard.bool(forKey: TravaDoApp.chave) {
            return .result(dialog: IntentDialog(stringLiteral: String(localized: "Com a trava do Piggu ligada, abra o app para ver seus gastos.")))
        }
        let mes = Formatos.mes(Date())
        if let cliente = Sessao.atual?.cliente,
           let resumo = try? await cliente.resumoDoMes(mes) {
            let limite = (try? await cliente.listarMetas())?[mes]
            return .result(dialog: IntentDialog(stringLiteral: Self.fala(gasto: resumo.gastos, limite: limite, mes: mes)))
        }
        if let guardado = ResumoDoWidget.ler(), guardado.mes == mes {
            let fala = Self.fala(gasto: guardado.gasto, limite: guardado.limite, mes: mes)
            let quando = guardado.atualizadoEm.formatted(.dateTime.day().month().hour().minute().locale(Formatos.localidade))
            return .result(dialog: IntentDialog(stringLiteral: fala + " " + String(localized: "(Conta de \(quando); estou sem conexão agora.)")))
        }
        return .result(dialog: IntentDialog(stringLiteral: String(localized: "Não consegui ver seus gastos agora. Abra o Piggu uma vez e pergunte de novo.")))
    }

    /// "Em outubro de 2026 você gastou R$ 1.039,39. Ainda dá para gastar R$ 1.460,61 do limite de R$ 2.500,00."
    static func fala(gasto: Decimal, limite: Decimal?, mes: String) -> String {
        let nome = Formatos.nomeDoMes(chave: mes).lowercased(with: Formatos.localidade)
        let gastei = String(localized: "Em \(nome) você gastou \(Formatos.moeda(gasto)).")
        guard let limite, limite > 0 else { return gastei }
        let resto = limite - gasto
        return gastei + " " + (resto >= 0
            ? String(localized: "Ainda dá para gastar \(Formatos.moeda(resto)) do limite de \(Formatos.moeda(limite)).")
            : String(localized: "Passou \(Formatos.moeda(-resto)) do limite de \(Formatos.moeda(limite))."))
    }
}

/// As frases que a Siri reconhece sem configurar nada (AtalhosDoPiggu do app Capacitor, com a
/// pergunta nova).
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
            systemImageName: "plus.circle")
        AppShortcut(
            intent: QuantoGasteiIntent(),
            phrases: [
                "Quanto gastei este mês no \(.applicationName)",
                "Quanto eu gastei no \(.applicationName)",
                "Gastos do mês no \(.applicationName)",
            ],
            shortTitle: "Quanto gastei",
            systemImageName: "chart.bar")
    }
}
