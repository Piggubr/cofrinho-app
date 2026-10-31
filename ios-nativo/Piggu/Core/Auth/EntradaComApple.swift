import AuthenticationServices
import CryptoKit
import Foundation

/// Entrar com a Apple (D-072): o app gera um nonce, manda o SHA-256 dele (hexadecimal) para a
/// Apple e o nonce original para POST /auth/apple, que confere o hash dentro do token.
struct EntradaComApple: Equatable, Sendable {
    let nonce: String

    init(nonce: String = EntradaComApple.novoNonce()) {
        self.nonce = nonce
    }

    /// O que vai no `ASAuthorizationAppleIDRequest`.
    var nonceComHash: String {
        SHA256.hash(data: Data(nonce.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    func preparar(_ pedido: ASAuthorizationAppleIDRequest) {
        pedido.requestedScopes = [.fullName, .email]
        pedido.nonce = nonceComHash
    }

    /// O identity token, o nome (so na primeira autorizacao) e o codigo de autorizacao, que o
    /// backend troca por um refresh token para revogar o acesso quando a conta for excluida.
    static func credencial(de autorizacao: ASAuthorization) -> (idToken: String, nome: String?, codigo: String?)? {
        guard let credencial = autorizacao.credential as? ASAuthorizationAppleIDCredential,
              let dados = credencial.identityToken,
              let idToken = String(data: dados, encoding: .utf8) else {
            return nil
        }
        let nome = credencial.fullName.map { PersonNameComponentsFormatter().string(from: $0) }
        let codigo = credencial.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
        return (idToken, nome?.isEmpty == false ? nome : nil, codigo)
    }

    private static func novoNonce() -> String {
        // 32 bytes do gerador seguro do sistema.
        SymmetricKey(size: .bits256).withUnsafeBytes { Data($0) }.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
