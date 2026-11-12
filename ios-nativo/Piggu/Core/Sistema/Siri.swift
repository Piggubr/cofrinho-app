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
        if let sessao = Sessao.atual, let resumo = try? await sessao.cliente.resumoDoMes(mes) {
            let cliente = sessao.cliente
            // O que esta na fila do aparelho conta como no Painel (BUG-010).
            let gasto = resumo.gastos + sessao.fila.valorNaFila(mes: mes)
            async let limites = try? cliente.listarMetas()
            async let contas = try? cliente.contasFixas(mes)
            let limite = await limites?[mes] ?? 0
            // A mesma conta do Painel (D-195); sem as contas, so o gasto, nunca um numero inflado.
            let conta = await contas.flatMap {
                AindaDaParaGastar.calcular(limite: limite, receitas: resumo.receitas, gasto: gasto, contas: $0, mes: mes)
            }
            return .result(dialog: IntentDialog(stringLiteral: Self.fala(gasto: gasto, conta: conta, mes: mes)))
        }
        if let guardado = ResumoDoWidget.ler(), guardado.mes == mes {
            let fala = Self.fala(gasto: guardado.gasto, conta: guardado.aindaDa, mes: mes)
            let quando = guardado.atualizadoEm.formatted(.dateTime.day().month().hour().minute().locale(Formatos.localidade))
            return .result(dialog: IntentDialog(stringLiteral: fala + " " + String(localized: "(Conta de \(quando); estou sem conexão agora.)")))
        }
        return .result(dialog: IntentDialog(stringLiteral: String(localized: "Não consegui ver seus gastos agora. Abra o Piggu uma vez e pergunte de novo.")))
    }

    /// "Em outubro de 2026 você gastou R$ 1.039,39. Ainda dá para gastar R$ 820,61, contando as
    /// contas a pagar." O número é o do Painel (`AindaDaParaGastar`, D-195).
    static func fala(gasto: Decimal, conta: AindaDaParaGastar?, mes: String) -> String {
        let nome = Formatos.nomeDoMes(chave: mes).lowercased(with: Formatos.localidade)
        let gastei = String(localized: "Em \(nome) você gastou \(Formatos.moeda(gasto)).")
        guard let conta else { return gastei }
        return gastei + " " + (conta.resta >= 0
            ? String(localized: "Ainda dá para gastar \(Formatos.moeda(conta.resta)), contando as contas a pagar.")
            : String(localized: "Já passou \(Formatos.moeda(-conta.resta)) do que dava para gastar, contando as contas a pagar."))
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
