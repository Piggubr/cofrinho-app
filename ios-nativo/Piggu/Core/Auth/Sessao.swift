import Foundation
import Observation

/// Entrada e saida da conta (espelho de auth.service.ts).
///
/// O access token fica so em memoria, dentro do ClienteDaAPI. O refresh vive no
/// cookie HttpOnly que o identity entrega; o HTTPCookieStorage do URLSession o guarda
/// e o manda sozinho para /api/auth/refresh. Com "continuar conectado" o cookie tem
/// validade e sobrevive ao fechar o app; sem, e de sessao e some com o processo. Com
/// `ConfiguracaoDaAPI.refreshNoCorpo`, o refresh vem no corpo e fica no Keychain.
///
/// O perfil fica no cache: sem internet ao abrir, o app entra com ele e mostra o guardado.
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
    let cache: Cache

    private static let chaveDoUsuario = "usuario"

    var usuario: Usuario? {
        if case let .logado(usuario) = estado {
            return usuario
        }
        return nil
    }

    init(cliente: ClienteDaAPI, cache: Cache) {
        self.cliente = cliente
        self.cache = cache
        Task { [weak self] in
            await cliente.configurar(
                aoRenovar: { usuario in
                    await MainActor.run { self?.entrou(usuario) }
                },
                aoPerderSessao: {
                    await MainActor.run { self?.saiu() }
                })
        }
    }

    /// Ao abrir o app: sem como ler o cookie (HttpOnly), pergunta ao identity.
    func restaurar() async {
        do {
            let tokens = try await cliente.renovar()
            entrou(tokens.usuario)
        } catch ErroDaAPI.semConexao {
            // A primeira chamada com rede renova sozinha (401, refresh, repete).
            if let usuario = cache.ler(Self.chaveDoUsuario, como: Usuario.self)?.valor {
                estado = .logado(usuario)
            } else {
                estado = .deslogado
            }
        } catch {
            saiu()
        }
    }

    func entrarComSenha(email: String, senha: String, lembrar: Bool) async throws {
        let pedido = PedidoDeLogin(
            email: email.trimmingCharacters(in: .whitespacesAndNewlines), senha: senha, lembrar: lembrar,
            refreshNoCorpo: cliente.pedeRefreshNoCorpo ? true : nil)
        let tokens: ParDeTokens = try await cliente.post("/auth/entrar", corpo: pedido, aberta: true)
        await cliente.guardar(tokens, lembrar: lembrar)
        entrou(tokens.usuario)
    }

    // TODO: entrarComGoogle (POST /auth/google com o ID token do Google Sign-In).
    // TODO: entrarComApple (POST /auth/apple, com nonce; D-072).

    /// Busca o perfil atualizado (GET /auth/me).
    func atualizarPerfil() async {
        if let usuario: Usuario = try? await cliente.get("/auth/me") {
            entrou(usuario)
        }
    }

    func sair() async {
        await cliente.sair()
        apagarCookies()
        saiu()
    }

    private func entrou(_ usuario: Usuario) {
        cache.guardar(usuario, em: Self.chaveDoUsuario)
        estado = .logado(usuario)
    }

    /// Os dados guardados sao da conta: saem com ela.
    private func saiu() {
        cache.apagarTudo()
        estado = .deslogado
    }

    private func apagarCookies() {
        let armazenamento = HTTPCookieStorage.shared
        for cookie in armazenamento.cookies(for: cliente.urlBase.appending(path: "auth")) ?? [] {
            armazenamento.deleteCookie(cookie)
        }
    }
}
