import Foundation
import Observation

/// Entrada e saida da conta (espelho de auth.service.ts).
///
/// O access token fica so em memoria, dentro do ClienteDaAPI. O refresh vive no
/// cookie HttpOnly que o identity entrega; o HTTPCookieStorage do URLSession o guarda
/// e o manda sozinho para /api/auth/refresh. Com "continuar conectado" o cookie tem
/// validade e sobrevive ao fechar o app; sem, e de sessao e some com o processo.
@MainActor
@Observable
final class Sessao {
    enum Estado: Equatable {
        case verificando
        case deslogado
        case logado(Usuario)
    }

    private(set) var estado: Estado = .verificando
    let cliente: ClienteDaAPI

    var usuario: Usuario? {
        if case let .logado(usuario) = estado {
            return usuario
        }
        return nil
    }

    init(cliente: ClienteDaAPI) {
        self.cliente = cliente
        Task { [weak self] in
            await cliente.configurar(
                aoRenovar: { usuario in
                    await MainActor.run { self?.estado = .logado(usuario) }
                },
                aoPerderSessao: {
                    await MainActor.run { self?.estado = .deslogado }
                })
        }
    }

    /// Ao abrir o app: sem como ler o cookie (HttpOnly), pergunta ao identity.
    func restaurar() async {
        do {
            let tokens = try await cliente.renovar()
            estado = .logado(tokens.usuario)
        } catch {
            estado = .deslogado
        }
    }

    func entrarComSenha(email: String, senha: String, lembrar: Bool) async throws {
        let pedido = PedidoDeLogin(
            email: email.trimmingCharacters(in: .whitespacesAndNewlines), senha: senha, lembrar: lembrar)
        let tokens: ParDeTokens = try await cliente.post("/auth/entrar", corpo: pedido, aberta: true)
        await cliente.guardar(tokens)
        estado = .logado(tokens.usuario)
    }

    // TODO: entrarComGoogle (POST /auth/google com o ID token do Google Sign-In).
    // TODO: entrarComApple (o backend ainda nao tem a rota).

    /// Busca o perfil atualizado (GET /auth/me).
    func atualizarPerfil() async {
        if let usuario: Usuario = try? await cliente.get("/auth/me") {
            estado = .logado(usuario)
        }
    }

    func sair() async {
        // Avisar o servidor e o que encerra a sessao e apaga o cookie; falhar aqui nao
        // pode impedir a pessoa de sair deste aparelho.
        let _: SemCorpo? = try? await cliente.post("/auth/logout", corpo: SemCorpo(), aberta: true)
        await cliente.esquecerToken()
        apagarCookies()
        estado = .deslogado
    }

    private func apagarCookies() {
        let armazenamento = HTTPCookieStorage.shared
        for cookie in armazenamento.cookies(for: cliente.urlBase.appending(path: "auth")) ?? [] {
            armazenamento.deleteCookie(cookie)
        }
    }
}
