import LocalAuthentication
import Observation
import SwiftUI

/// Travar o app com Face ID (ou Touch ID, ou o codigo do iPhone) ao abrir e ao voltar do fundo,
/// ligado no Perfil. Vale para este aparelho.
@MainActor
@Observable
final class TravaDoApp {
    static let chave = "piggu_trava_biometrica"

    private(set) var travado: Bool
    private(set) var verificando = false

    var ligada: Bool {
        didSet { UserDefaults.standard.set(ligada, forKey: Self.chave) }
    }

    init() {
        let ligada = UserDefaults.standard.bool(forKey: Self.chave)
        self.ligada = ligada
        travado = ligada
    }

    /// "Face ID", "Touch ID" ou "Optic ID", para os textos; nil quando o aparelho nao tem.
    var nomeDaBiometria: String? {
        let contexto = LAContext()
        guard contexto.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else { return nil }
        switch contexto.biometryType {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        case .opticID: return "Optic ID"
        default: return nil
        }
    }

    /// O app foi para o fundo: na volta, pede de novo.
    func travarSeLigada() {
        if ligada { travado = true }
    }

    /// - Returns: se a pessoa provou que e ela.
    @discardableResult
    func desbloquear() async -> Bool {
        guard !verificando else { return false }
        verificando = true
        defer { verificando = false }
        let ok = await Self.autenticar(motivo: String(localized: "Desbloquear o Piggu"))
        if ok { travado = false }
        return ok
    }

    /// Ligar pede a prova antes, para ninguem travar o app de outra pessoa.
    func ligar(_ ligar: Bool) async {
        if ligar {
            guard await Self.autenticar(motivo: String(localized: "Travar o Piggu com a sua biometria")) else { return }
        }
        ligada = ligar
        travado = false
    }

    private static func autenticar(motivo: String) async -> Bool {
        // .deviceOwnerAuthentication: sem biometria (ou depois de falhar), vale o codigo do iPhone.
        (try? await LAContext().evaluatePolicy(.deviceOwnerAuthentication, localizedReason: motivo)) ?? false
    }
}

/// O vidro por cima do app travado, com o porquinho e o botao de desbloquear.
struct TelaDeTrava: View {
    let trava: TravaDoApp

    @Environment(\.paleta) private var paleta

    var body: some View {
        VStack(spacing: 18) {
            Image("Logo").resizable().scaledToFit().frame(width: 88, height: 88)
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                .accessibilityHidden(true)
            Text("O Piggu está travado").font(.sora(.title3, peso: .semibold))
            Button {
                Task { await trava.desbloquear() }
            } label: {
                Label("Desbloquear com \(trava.nomeDaBiometria ?? String(localized: "o código"))",
                      systemImage: trava.nomeDaBiometria == "Touch ID" ? "touchid" : "faceid")
                    .padding(.horizontal, 8)
            }
            .botaoPrincipal()
            .disabled(trava.verificando)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(.ultraThinMaterial)
        .background(paleta.fundo.opacity(0.6))
        .ignoresSafeArea()
        .task { await trava.desbloquear() }
        .accessibilityAddTraits(.isModal)
    }
}
