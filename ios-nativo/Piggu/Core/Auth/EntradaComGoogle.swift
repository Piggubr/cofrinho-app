import GoogleSignIn
import UIKit

/// Google Sign-In nativo (D-055): devolve o ID token que vai para POST /auth/google. O ID OAuth
/// do iOS vem do `Config/Piggu.xcconfig`; sem ele o botao nao aparece.
@MainActor
enum EntradaComGoogle {
    /// O GIDClientID do Info.plist, preenchido pelo build setting GOOGLE_IOS_CLIENT_ID.
    static var disponivel: Bool {
        let id = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String ?? ""
        return id.hasSuffix(".apps.googleusercontent.com")
    }

    /// - Returns: o ID token, ou nil quando a pessoa fechou a tela do Google.
    static func entrar() async throws -> String? {
        guard let tela = telaDeCima() else { return nil }
        do {
            let resultado = try await GIDSignIn.sharedInstance.signIn(withPresenting: tela)
            return resultado.user.idToken?.tokenString
        } catch let erro as GIDSignInError where erro.code == .canceled {
            return nil
        }
    }

    /// Ao sair: a proxima entrada pergunta de novo qual conta usar.
    static func esquecer() {
        guard disponivel else { return }
        GIDSignIn.sharedInstance.signOut()
    }

    /// A volta do navegador do Google; devolve verdadeiro quando o link era dele.
    static func receber(_ url: URL) -> Bool {
        disponivel && GIDSignIn.sharedInstance.handle(url)
    }

    private static func telaDeCima() -> UIViewController? {
        let janela = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
        var tela = janela?.rootViewController
        while let apresentada = tela?.presentedViewController {
            tela = apresentada
        }
        return tela
    }
}
