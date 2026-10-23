import Foundation
import Testing
@testable import Piggu

/// Dublê que responde na ordem combinada e guarda o que recebeu.
private actor TransporteRoteirizado: Transporte {
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

private let tokensNovos = """
{"accessToken":"novo","expiresIn":1800,"usuario":{"id":"u1","email":"ana@example.com","nome":"Ana Souza",
"primeiroNome":"Ana","apelido":null,"foto":null,"role":"MEMBRO","ativo":true,"plano":"GRATUITO",
"premiumAte":null,"familia":"f1"}}
"""

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

        await #expect(throws: ErroDaAPI.servidor(status: 400, mensagem: "E-mail ou senha incorretos.")) {
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
}
