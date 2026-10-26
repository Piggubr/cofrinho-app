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

    // MARK: - Entrar

    func entrarComSenha(email: String, senha: String, lembrar: Bool) async throws {
        let pedido = PedidoDeLogin(
            email: email.trimmingCharacters(in: .whitespacesAndNewlines), senha: senha, lembrar: lembrar,
            refreshNoCorpo: refreshNoCorpo)
        try await abrirSessao("/auth/entrar", corpo: pedido, lembrar: lembrar)
    }

    /// Troca o ID token do Google por um par de tokens do Piggu.
    /// - Parameter versaoDosTermos: so para conta nova, depois do TERMOS_NECESSARIOS.
    func entrarComGoogle(idToken: String, lembrar: Bool, versaoDosTermos: String? = nil) async throws {
        let pedido = PedidoDoGoogle(idToken: idToken, versaoDosTermos: versaoDosTermos, lembrar: lembrar,
                                    refreshNoCorpo: refreshNoCorpo)
        try await abrirSessao("/auth/google", corpo: pedido, lembrar: lembrar)
    }

    /// Troca o identity token da Apple por um par de tokens do Piggu (D-072).
    /// - Parameter nome: a Apple so entrega na primeira autorizacao; vira o apelido da conta nova.
    func entrarComApple(
        idToken: String, nonce: String, nome: String?, lembrar: Bool, versaoDosTermos: String? = nil
    ) async throws {
        let pedido = PedidoDaApple(idToken: idToken, nonce: nonce, nome: nome, versaoDosTermos: versaoDosTermos,
                                   lembrar: lembrar, refreshNoCorpo: refreshNoCorpo)
        try await abrirSessao("/auth/apple", corpo: pedido, lembrar: lembrar)
    }

    private var refreshNoCorpo: Bool? { cliente.pedeRefreshNoCorpo ? true : nil }

    private func abrirSessao<Corpo: Encodable & Sendable>(_ rota: String, corpo: Corpo, lembrar: Bool) async throws {
        let tokens: ParDeTokens = try await cliente.post(rota, corpo: corpo, aberta: true)
        // O cache e da conta: o de quem entrou antes nao serve para esta.
        cache.apagarTudo()
        await cliente.guardar(tokens, lembrar: lembrar)
        entrou(tokens.usuario)
    }

    // MARK: - Sem sessao (links do e-mail)

    /// Cria a conta sem abrir sessao: a pessoa confirma o e-mail e entra com a senha. O
    /// backend responde igual se o e-mail ja tem conta.
    func cadastrar(_ pedido: PedidoDeCadastro) async throws {
        let _: SemCorpo = try await cliente.post("/auth/cadastro", corpo: pedido, aberta: true)
    }

    /// Pede o link de senha nova; o backend responde igual com ou sem conta.
    func esqueciSenha(email: String) async throws {
        let _: SemCorpo = try await cliente.post("/auth/senha/esqueci", corpo: PedidoDeEmail(email: email), aberta: true)
    }

    /// Senha nova pelo link do e-mail. Todas as sessoes da conta caem: se havia uma aqui,
    /// sai dela tambem.
    func redefinirSenha(token: String, novaSenha: String) async throws {
        let _: SemCorpo = try await cliente.post(
            "/auth/senha/redefinir", corpo: PedidoDeSenhaNova(token: token, novaSenha: novaSenha), aberta: true)
        if usuario != nil {
            await encerrarLocalmente()
        }
    }

    /// Confirma o e-mail pelo link; com sessao aberta, o perfil ja sai atualizado.
    func confirmarEmail(token: String) async throws {
        let _: SemCorpo = try await cliente.post("/auth/confirmar-email", corpo: PedidoComToken(token: token), aberta: true)
        if usuario != nil {
            await atualizarPerfil()
        }
    }

    // MARK: - Conta (users.service.ts)

    /// Manda outro link de confirmacao; o anterior deixa de valer.
    func reenviarConfirmacao() async throws {
        let _: SemCorpo = try await cliente.post("/auth/me/email/reenviar", corpo: [String: String]())
    }

    /// Busca o perfil atualizado (GET /auth/me).
    func atualizarPerfil() async {
        if let usuario: Usuario = try? await cliente.get("/auth/me") {
            entrou(usuario)
        }
    }

    /// Aceite da versao vigente dos Termos e do Aviso.
    func aceitarTermos() async throws {
        entrou(try await cliente.put("/me/termos", corpo: PedidoDeTermos(versao: Conta.versaoDosTermos)))
    }

    /// Como quer ser chamado e, so na primeira vez, a data de nascimento (aaaa-mm-dd).
    func sobreVoce(apelido: String, nascimento: String?) async throws {
        entrou(try await cliente.put("/me/sobre-voce", corpo: PedidoSobreVoce(apelido: apelido, nascimento: nascimento)))
    }

    /// Pais onde mora e o CPF. Depois renova o token: sem isso os outros servicos ainda veem
    /// a conta como incompleta.
    func informarPais(_ pais: String, documento: String?) async throws {
        entrou(try await cliente.put("/me/pais", corpo: PedidoDePais(pais: pais, documento: documento)))
        try await cliente.renovar()
    }

    /// Apaga a conta no servidor e sai deste aparelho.
    func excluirConta() async throws {
        try await cliente.delete("/me")
        await encerrarLocalmente()
    }

    func sair() async {
        await cliente.sair()
        await encerrarLocalmente()
    }

    /// Descarta a sessao local sem falar com o servidor.
    private func encerrarLocalmente() async {
        await cliente.esquecerTokens()
        apagarCookies()
        EntradaComGoogle.esquecer()
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
