import SwiftUI
import UIKit

/// Icone do app na tela inicial (Premium), os mesmos do app Capacitor (IconeDoAppPlugin.swift e os
/// AppIcon-<id> do Assets.xcassets). Cada um traz a versao escura, que o iOS usa sozinho no modo
/// escuro. O sistema mostra um aviso a cada troca e nao deixa esconder.
enum IconeDoApp: String, CaseIterable, Identifiable, Sendable {
    case padrao, classico, monograma, silhueta, azul, menta, lilas, coral, mel, vinho, grafite, namorados

    var id: String { rawValue }

    var nome: LocalizedStringKey {
        switch self {
        case .padrao: "Neon"
        case .classico: "Clássico"
        case .monograma: "P de Piggu"
        case .silhueta: "Silhueta"
        case .azul: "Azul"
        case .menta: "Menta"
        case .lilas: "Lilás"
        case .coral: "Coral"
        case .mel: "Mel"
        case .vinho: "Vinho"
        case .grafite: "Grafite"
        case .namorados: "Namorados"
        }
    }

    /// O padrao e o classico sao gratis; os outros sao do Premium.
    var premium: Bool { self != .padrao && self != .classico }

    /// A mesma arte do icone de verdade (frontend/public/icones-do-app).
    var previa: Image { Image("IconeDoApp-\(rawValue)") }

    /// Nome do conjunto alternativo no Assets.xcassets; nil e o principal.
    var nomeAlternativo: String? { self == .padrao ? nil : "AppIcon-\(rawValue)" }

    @MainActor
    static var atual: IconeDoApp {
        let nome = UIApplication.shared.alternateIconName
        return allCases.first { $0.nomeAlternativo == nome } ?? .padrao
    }

    @MainActor
    func aplicar() async throws {
        guard UIApplication.shared.supportsAlternateIcons, Self.atual != self else { return }
        try await UIApplication.shared.setAlternateIconName(nomeAlternativo)
    }

    /// O Premium acabou: um icone do Premium volta ao padrao (o Classico fica), como no site.
    /// - Returns: se voltou, para a tela avisar.
    @MainActor
    @discardableResult
    static func voltarAoPadraoSemPremium(_ premium: Bool) async -> Bool {
        guard !premium, atual.premium else { return false }
        try? await IconeDoApp.padrao.aplicar()
        return true
    }
}

/// Automatico segue o aparelho; vale para este aparelho, na mesma chave do site.
enum ModoDeAparencia: String, CaseIterable, Identifiable {
    case automatico, claro, escuro

    static let chave = "piggu_modo"

    var id: String { rawValue }

    var nome: LocalizedStringKey {
        switch self {
        case .automatico: "Automático"
        case .claro: "Claro"
        case .escuro: "Escuro"
        }
    }

    var esquema: ColorScheme? {
        switch self {
        case .automatico: nil
        case .claro: .light
        case .escuro: .dark
        }
    }
}
