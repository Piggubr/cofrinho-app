import Foundation
import Testing
@testable import Piggu

/// Dublê que responde na ordem combinada e guarda o que recebeu.
actor TransporteRoteirizado: Transporte {
    private var roteiro: [(status: Int, corpo: String)]
    private(set) var recebidos: [URLRequest] = []

    init(_ roteiro: [(status: Int, corpo: String)]) {
        self.roteiro = roteiro
    }

    func enviar(_ pedido: URLRequest) async throws -> (Data, HTTPURLResponse) {
        recebidos.append(pedido)
        let proxima = roteiro.isEmpty ? (status: 500, corpo: "") : roteiro.removeFirst()
        let resposta = HTTPURLResponse(url: pedido.url!, statusCode: proxima.status, httpVersion: nil, headerFields: nil)!
        return (Data(proxima.corpo.utf8), resposta)
    }
}

let tokensNovos = """
{"accessToken":"novo","expiresIn":1800,"usuario":{"id":"u1","email":"ana@example.com","nome":"Ana Souza",
"primeiroNome":"Ana","apelido":null,"foto":null,"role":"MEMBRO","ativo":true,"plano":"GRATUITO",
"premiumAte":null,"familia":"f1"}}
"""

/// Cofre em memoria, no lugar do Keychain.
private final class CofreDeTeste: CofreDoRefresh, @unchecked Sendable {
    private let trava = NSLock()
    private var guardado: String?

    init(_ inicial: String? = nil) {
        guardado = inicial
    }

    func ler() -> String? { trava.withLock { guardado } }
    func guardar(_ refresh: String) { trava.withLock { guardado = refresh } }
    func apagar() { trava.withLock { guardado = nil } }
}

private func tokens(access: String, refresh: String? = nil) -> String {
    let par = refresh.map { #","refreshToken":"\#($0)""# } ?? ""
    return tokensNovos.replacingOccurrences(of: #""accessToken":"novo""#, with: #""accessToken":"\#(access)"\#(par)"#)
}

private func corpo(_ pedido: URLRequest) -> [String: String] {
    (pedido.httpBody.flatMap { try? JSONSerialization.jsonObject(with: $0) } as? [String: String]) ?? [:]
}

struct ClienteDaAPITests {
    private let base = URL(string: "https://piggu.com.br/api")!

    @Test func renovaUmaVezNo401ERepeteComOTokenNovo() async throws {
        let transporte = TransporteRoteirizado([
            (401, #"{"erro":"Token vencido."}"#),
            (200, tokensNovos),
            (200, #"{"categorias":["Casa"]}"#),
        ])
        let cliente = ClienteDaAPI(urlBase: base, transporte: transporte)
        let velho = try JSONDecoder().decode(ParDeTokens.self, from: Data(tokensNovos.utf8))
        await cliente.guardar(ParDeTokens(accessToken: "velho", expiresIn: 1800, usuario: velho.usuario))

        let categorias = try await cliente.listarCategorias()

        #expect(categorias == ["Casa"])
        let recebidos = await transporte.recebidos
        #expect(recebidos.map { $0.url!.absoluteString } == [
            "https://piggu.com.br/api/categories",
            "https://piggu.com.br/api/auth/refresh",
            "https://piggu.com.br/api/categories",
        ])
        #expect(recebidos[0].value(forHTTPHeaderField: "Authorization") == "Bearer velho")
        // O refresh vai pelo cookie, sem o header.
        #expect(recebidos[1].value(forHTTPHeaderField: "Authorization") == nil)
        #expect(recebidos[1].httpMethod == "POST")
        #expect(recebidos[2].value(forHTTPHeaderField: "Authorization") == "Bearer novo")
    }

    @Test func refreshRecusadoEncerraASessao() async throws {
        let transporte = TransporteRoteirizado([
            (401, ""),
            (401, #"{"erro":"Sua sessao venceu. Entre novamente."}"#),
        ])
        let cliente = ClienteDaAPI(urlBase: base, transporte: transporte)

        await #expect(throws: ErroDaAPI.sessaoEncerrada) {
            _ = try await cliente.listarGastos(mes: "2026-10")
        }
        #expect(await transporte.recebidos.count == 2)
    }

    @Test func mensagemDoBackendChegaNoErro() async throws {
        let transporte = TransporteRoteirizado([(400, #"{"erro":"E-mail ou senha incorretos.","codigo":"X"}"#)])
        let cliente = ClienteDaAPI(urlBase: base, transporte: transporte)

        await #expect(throws: ErroDaAPI.servidor(status: 400, mensagem: "E-mail ou senha incorretos.", codigo: "X")) {
            let _: ParDeTokens = try await cliente.post(
                "/auth/entrar", corpo: PedidoDeLogin(email: "a@b.c", senha: "x", lembrar: true), aberta: true)
        }
        let pedido = try #require(await transporte.recebidos.first)
        #expect(pedido.url?.absoluteString == "https://piggu.com.br/api/auth/entrar")
        #expect(pedido.value(forHTTPHeaderField: "Content-Type") == "application/json")
    }

    @Test func mesVaiNaQueryString() async throws {
        let transporte = TransporteRoteirizado([(200, "[]")])
        let cliente = ClienteDaAPI(urlBase: base, transporte: transporte)
        _ = try await cliente.listarGastos(mes: "2026-10")
        #expect(await transporte.recebidos.first?.url?.absoluteString == "https://piggu.com.br/api/expenses?mes=2026-10")
    }

    // MARK: - Refresh no corpo (D-072)

    @Test func semCofreRenovaPeloCookieComCorpoVazio() async throws {
        let transporte = TransporteRoteirizado([(200, tokensNovos)])
        let cliente = ClienteDaAPI(urlBase: base, transporte: transporte, cofre: nil)

        try await cliente.renovar()

        let pedido = try #require(await transporte.recebidos.first)
        #expect(String(data: pedido.httpBody ?? Data(), encoding: .utf8) == "{}")
        #expect(!cliente.pedeRefreshNoCorpo)
    }

    @Test func comCofreRenovaPeloCorpoEGuardaORefreshNovo() async throws {
        let cofre = CofreDeTeste("r1")
        let transporte = TransporteRoteirizado([(200, tokens(access: "a2", refresh: "r2"))])
        let cliente = ClienteDaAPI(urlBase: base, transporte: transporte, cofre: cofre)

        let par = try await cliente.renovar()

        #expect(par.accessToken == "a2")
        let pedido = try #require(await transporte.recebidos.first)
        #expect(pedido.url?.path == "/api/auth/refresh")
        #expect(corpo(pedido) == ["refreshToken": "r1"])
        // O backend rotaciona: o velho nao vale mais.
        #expect(cofre.ler() == "r2")
    }

    @Test func loginGuardaORefreshSoComContinuarConectado() async throws {
        let cofre = CofreDeTeste()
        let cliente = ClienteDaAPI(urlBase: base, transporte: TransporteRoteirizado([]), cofre: cofre)
        let par = try JSONDecoder().decode(ParDeTokens.self, from: Data(tokens(access: "a", refresh: "r").utf8))

        await cliente.guardar(par, lembrar: false)
        #expect(cofre.ler() == nil)

        await cliente.guardar(par, lembrar: true)
        #expect(cofre.ler() == "r")
    }

    @Test func refreshRecusadoApagaOCofre() async throws {
        let cofre = CofreDeTeste("r1")
        let transporte = TransporteRoteirizado([(401, #"{"erro":"Sua sessao venceu. Entre novamente."}"#)])
        let cliente = ClienteDaAPI(urlBase: base, transporte: transporte, cofre: cofre)

        await #expect(throws: ErroDaAPI.self) { try await cliente.renovar() }
        #expect(cofre.ler() == nil)
    }

    @Test func semRedeNaRenovacaoNaoEncerraASessao() async throws {
        let cofre = CofreDeTeste("r1")
        let transporte = TransporteRoteirizado([(401, "")])
        let cliente = ClienteDaAPI(urlBase: base, transporte: SemRedeDepoisDe(transporte, pedidos: 1), cofre: cofre)
        let perdeu = Sinal()
        await cliente.configurar(aoRenovar: { _ in }, aoPerderSessao: { await perdeu.marcar() })

        await #expect(throws: ErroDaAPI.semConexao) { _ = try await cliente.listarCategorias() }
        #expect(await !perdeu.marcado)
        #expect(cofre.ler() == "r1")
    }

    @Test func sairMandaORefreshNoCorpoEEsqueceTudo() async throws {
        let cofre = CofreDeTeste("r1")
        let transporte = TransporteRoteirizado([(204, "")])
        let cliente = ClienteDaAPI(urlBase: base, transporte: transporte, cofre: cofre)

        await cliente.sair()

        let pedido = try #require(await transporte.recebidos.first)
        #expect(pedido.url?.path == "/api/auth/logout")
        #expect(corpo(pedido) == ["refreshToken": "r1"])
        #expect(cofre.ler() == nil)
        #expect(await !cliente.temToken)
    }
}

/// Responde pelo transporte nos primeiros pedidos e depois finge que a rede caiu.
private actor SemRedeDepoisDe: Transporte {
    private let transporte: Transporte
    private var restantes: Int

    init(_ transporte: Transporte, pedidos: Int) {
        self.transporte = transporte
        restantes = pedidos
    }

    func enviar(_ pedido: URLRequest) async throws -> (Data, HTTPURLResponse) {
        guard restantes > 0 else { throw URLError(.notConnectedToInternet) }
        restantes -= 1
        return try await transporte.enviar(pedido)
    }
}

private actor Sinal {
    private(set) var marcado = false
    func marcar() { marcado = true }
}
