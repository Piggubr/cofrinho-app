import AuthenticationServices
import Foundation
import Testing
@testable import Piggu

@MainActor
struct OpenFinanceTests {
    private let base = URL(string: "https://piggu.com.br/api")!
    private let contas = #"[{"id":"a1","conexaoId":"cx","instituicao":"Nubank","nome":"Conta","numero":"1","saldo":10.5,"moeda":"BRL"}]"#

    @Test func paginaDoSiteLevaOTokenNoFragmento() {
        let pagina = OpenFinance.pagina(ConnectToken(accessToken: "abc+/=", sandbox: true), site: base)
        #expect(pagina.absoluteString.hasPrefix("https://piggu.com.br/conectar-banco#"))
        #expect(pagina.query == nil)
        let itens = URLComponents(string: "x://y?" + (pagina.fragment ?? ""))?.queryItems ?? []
        #expect(itens.first { $0.name == "token" }?.value == "abc+/=")
        #expect(itens.first { $0.name == "sandbox" }?.value == "1")

        let local = OpenFinance.pagina(ConnectToken(accessToken: "t", sandbox: false),
                                       site: URL(string: "http://localhost:8080/api")!)
        #expect(local.absoluteString == "http://localhost:8080/conectar-banco#token=t")
    }

    @Test func entendeAVoltaDoSite() {
        #expect(OpenFinance.resultado(URL(string: "piggu://banco-conectado?item=it%201")!) == .conectado("it 1"))
        #expect(OpenFinance.resultado(URL(string: "piggu://banco-conectado?fechou=1")!) == .fechou)
        #expect(OpenFinance.resultado(URL(string: "piggu://banco-conectado?erro=Banco%20fora")!) == .falhou("Banco fora"))
        #expect(OpenFinance.resultado(URL(string: "piggu://banco-conectado?item=")!) == .fechou)
    }

    @Test func conectarPedeOTokenAbreAPaginaERegistraOBanco() async {
        let transporte = TransporteRoteirizado([(201, #"{"accessToken":"tk","sandbox":false}"#), (201, contas)])
        let openFinance = OpenFinance(cliente: ClienteDaAPI(urlBase: base, transporte: transporte))
        var aberta: URL?

        await openFinance.conectar { pagina in
            aberta = pagina
            return URL(string: "piggu://banco-conectado?item=item-1")!
        }

        #expect(aberta?.absoluteString == "https://piggu.com.br/conectar-banco#token=tk")
        #expect(openFinance.contas.map(\.id) == ["a1"])
        #expect(openFinance.erro == nil)
        let recebidos = await transporte.recebidos
        #expect(recebidos.map { $0.url?.path } == ["/api/banking/connect-token", "/api/banking/items"])
        let autorizacao = recebidos.first?.httpBody.flatMap { try? JSONSerialization.jsonObject(with: $0) } as? [String: Any]
        #expect(autorizacao?["autorizo"] as? Bool == true)
        #expect(autorizacao?["versaoDoAviso"] as? String == Conta.versaoDosTermos)
        let item = recebidos.last?.httpBody.flatMap { try? JSONSerialization.jsonObject(with: $0) } as? [String: String]
        #expect(item == ["itemId": "item-1"])
    }

    @Test func fecharOuCancelarNaoRegistraEErroAparece() async {
        let cancelou = TransporteRoteirizado([(201, #"{"accessToken":"tk","sandbox":false}"#)])
        let semBanco = OpenFinance(cliente: ClienteDaAPI(urlBase: base, transporte: cancelou))
        await semBanco.conectar { _ in throw ASWebAuthenticationSessionError(.canceledLogin) }
        #expect(semBanco.erro == nil)
        #expect(await cancelou.recebidos.count == 1)

        let falhou = TransporteRoteirizado([(201, #"{"accessToken":"tk","sandbox":false}"#)])
        let comErro = OpenFinance(cliente: ClienteDaAPI(urlBase: base, transporte: falhou))
        await comErro.conectar { _ in URL(string: "piggu://banco-conectado?erro=Banco%20fora%20do%20ar")! }
        #expect(comErro.erro == "Banco fora do ar")
        #expect(await falhou.recebidos.count == 1)
    }
}
