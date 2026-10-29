import StoreKit
import StoreKitTest
import Testing
@testable import Piggu

/// A tela Premium e o Piggu.storekit (o mesmo que o esquema usa ao rodar no simulador).
@MainActor
struct LojaTests {
    private func plano(_ json: String) throws -> PlanoDaConta {
        try JSONDecoder().decode(PlanoDaConta.self, from: Data(json.utf8))
    }

    private let base = #""gratuito":["Gastos"],"premium":["Tudo do gratuito"],"diasDeTeste":7,"app":null,"planoFamilia":false,"planoCasal":false"#

    @Test func soOTitularSemAssinaturaNaCasaVeALoja() throws {
        let gratuito = try plano(#"{"plano":"GRATUITO","premiumAte":null,"origem":null,"voceAssinou":false,"casaTemAssinatura":false,"# + base + "}")
        #expect(PremiumView.podeAssinar(gratuito, titular: true))
        #expect(!PremiumView.podeAssinar(gratuito, titular: false))

        let outroAssinou = try plano(#"{"plano":"GRATUITO","premiumAte":null,"origem":"WEB","voceAssinou":false,"casaTemAssinatura":true,"# + base + "}")
        #expect(!PremiumView.podeAssinar(outroAssinou, titular: true))
    }

    @Test func premiumPeloSiteNaoVeALojaNemECobradoDeNovo() throws {
        let peloSite = try plano(#"{"plano":"PREMIUM","premiumAte":"2027-01-31T23:59:59Z","origem":"WEB","voceAssinou":true,"casaTemAssinatura":true,"# + base + "}")
        #expect(!PremiumView.podeAssinar(peloSite, titular: true))
        #expect(PremiumView.detalhe(peloSite)?.hasPrefix("Assinado pelo site") == true)

        let peloIPhone = try plano(#"{"plano":"PREMIUM","premiumAte":null,"origem":"APP_STORE","voceAssinou":true,"casaTemAssinatura":true,"# + base + "}")
        #expect(PremiumView.detalhe(peloIPhone)?.contains("App Store") == true)
    }

    @Test func produtosDoArquivoDeTesteBatemComOsDoApp() async throws {
        let sessao = try SKTestSession(configurationFileNamed: "Piggu")
        sessao.disableDialogs = true
        sessao.clearTransactions()
        let produtos = try await Product.products(for: LojaDaApple.produtos)
        #expect(Set(produtos.map(\.id)) == Set(LojaDaApple.produtos))
        #expect(produtos.allSatisfy { $0.type == .autoRenewable && $0.subscription?.introductoryOffer?.paymentMode == .freeTrial })
    }
}
