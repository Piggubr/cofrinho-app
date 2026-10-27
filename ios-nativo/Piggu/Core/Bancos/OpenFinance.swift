import AuthenticationServices
import Foundation
import Observation

/// Bancos conectados pelo Open Finance (Pluggy): os saldos, atualizar, desconectar e conectar
/// um banco novo, como o cartao do Painel do site (D-091).
///
/// Conectar: o app pede o token (com a autorizacao da pessoa), abre a pagina /conectar-banco do
/// site num ASWebAuthenticationSession efemero (o navegador do sistema, nunca um WebView) e
/// recebe o banco conectado em piggu://banco-conectado?item=...
@MainActor
@Observable
final class OpenFinance {
    /// Desligado no servidor (ou fora do ar): o cartao some.
    private(set) var habilitado = false
    private(set) var contas: [ContaBancaria] = []
    var erro: String?
    private(set) var ocupado = false

    @ObservationIgnored private let cliente: ClienteDaAPI

    init(cliente: ClienteDaAPI) {
        self.cliente = cliente
    }

    // Fora do resto de proposito: Open Finance e opcional, e fora do ar nao derruba a tela.
    func carregar() async {
        habilitado = await cliente.openFinanceHabilitado()
        guard habilitado else { return }
        do {
            contas = try await cliente.contasBancarias()
        } catch {
            self.erro = error.localizedDescription
        }
    }

    /// Um banco por conexao, para o desconectar nao repetir por conta.
    var bancos: [(conexaoId: String, instituicao: String)] {
        var vistos = Set<String>()
        return contas.compactMap { conta in
            vistos.insert(conta.conexaoId).inserted ? (conta.conexaoId, conta.instituicao) : nil
        }
    }

    /// Soma por moeda: somar real com euro daria um numero sem sentido.
    var saldoPorMoeda: [(moeda: String, total: Decimal)] {
        Dictionary(grouping: contas, by: \.moeda)
            .map { ($0.key, $0.value.reduce(0) { $0 + $1.saldo }) }
            .sorted { $0.0 < $1.0 }
    }

    func sincronizar() async {
        await usar { try await $0.sincronizarBancos() }
    }

    func desconectar(_ conexaoId: String) async {
        await usar { try await $0.desconectarBanco(conexaoId) }
    }

    /// - Parameter abrir: o ASWebAuthenticationSession da tela; devolve a URL de volta.
    func conectar(abrir: (URL) async throws -> URL) async {
        await usar { cliente in
            let token = try await cliente.gerarConnectToken(versaoDoAviso: Conta.versaoDosTermos)
            #if DEBUG
            // A demonstracao nao tem Pluggy: conecta um banco inventado sem abrir o navegador.
            if token.accessToken == "demo-connect" {
                return try await cliente.registrarItem("demo-item")
            }
            #endif
            let volta: URL
            do {
                volta = try await abrir(Self.pagina(token, site: cliente.urlBase))
            } catch let erro as ASWebAuthenticationSessionError where erro.code == .canceledLogin {
                return self.contas
            }
            switch Self.resultado(volta) {
            case let .conectado(item):
                return try await cliente.registrarItem(item)
            case .fechou:
                return self.contas
            case let .falhou(mensagem):
                throw ErroDaAPI.servidor(status: 422, mensagem: mensagem)
            }
        }
    }

    enum Resultado: Equatable {
        case conectado(String)
        case fechou
        case falhou(String)
    }

    /// https://piggu.com.br/conectar-banco#token=...: o token vai no fragmento, que nao sai do aparelho.
    static func pagina(_ token: ConnectToken, site api: URL) -> URL {
        var componentes = URLComponents(url: api, resolvingAgainstBaseURL: false)!
        let caminho = componentes.path.hasSuffix("/api") ? String(componentes.path.dropLast(4)) : componentes.path
        componentes.path = caminho + "/conectar-banco"
        var fragmento = URLComponents()
        fragmento.queryItems = [URLQueryItem(name: "token", value: token.accessToken)]
            + (token.sandbox ? [URLQueryItem(name: "sandbox", value: "1")] : [])
        componentes.percentEncodedFragment = fragmento.percentEncodedQuery
        return componentes.url!
    }

    /// piggu://banco-conectado?item=... | ?fechou=1 | ?erro=...
    static func resultado(_ volta: URL) -> Resultado {
        let itens = URLComponents(url: volta, resolvingAgainstBaseURL: false)?.queryItems ?? []
        if let item = itens.first(where: { $0.name == "item" })?.value, !item.isEmpty {
            return .conectado(item)
        }
        if let erro = itens.first(where: { $0.name == "erro" })?.value {
            return .falhou(erro)
        }
        return .fechou
    }

    private func usar(_ acao: (ClienteDaAPI) async throws -> [ContaBancaria]) async {
        ocupado = true
        erro = nil
        defer { ocupado = false }
        do {
            contas = try await acao(cliente)
        } catch {
            self.erro = error.localizedDescription
        }
    }
}
