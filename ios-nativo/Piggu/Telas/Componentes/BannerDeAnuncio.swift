import GoogleMobileAds
import SwiftUI
import UIKit

/// O que o banner do plano gratuito precisa saber do build e do SDK (D-180 e D-203).
@MainActor
enum Anuncios {
    private static var iniciado = false

    /// `-PigguDemo YES` nunca mostra anuncio.
    static var demo: Bool {
        #if DEBUG
        UserDefaults.standard.bool(forKey: "PigguDemo")
        #else
        false
        #endif
    }

    /// O id do banner (build setting `ADMOB_BANNER_ID`): o de teste no Debug, o real no Release.
    /// Vazio, o app sai sem banner.
    static var idDoBanner: String? {
        guard let id = Bundle.main.object(forInfoDictionaryKey: "PigguAdMobBanner") as? String,
              !id.isEmpty, !id.hasPrefix("$(") else { return nil }
        return id
    }

    /// Sobe o SDK uma vez, so quando o primeiro banner aparece, com a classificacao para todos os publicos.
    static func iniciar() {
        guard !iniciado else { return }
        iniciado = true
        MobileAds.shared.requestConfiguration.maxAdContentRating = .parentalGuidance
        MobileAds.shared.start(completionHandler: nil)
    }

    /// Sempre nao personalizado (`npa=1`) e sem nenhum dado do Piggu.
    static func pedido() -> Request {
        let pedido = Request()
        let extras = Extras()
        extras.additionalParameters = ["npa": "1"]
        pedido.register(extras)
        return pedido
    }
}

/// O banner adaptativo ancorado do plano gratuito. Fica com altura zero ate chegar um anuncio:
/// sem internet ou sem anuncio, nao sobra espaco vazio. A largura vem do espaco que a tela da,
/// nao da tela inteira (iPad, Split View, paisagem).
struct BannerDeAnuncio: View {
    @State private var altura: CGFloat = 0

    var body: some View {
        if let id = Anuncios.idDoBanner {
            GeometryReader { espaco in
                BannerDaAdMob(idDoBanner: id, largura: espaco.size.width, altura: $altura)
            }
            .frame(height: altura)
            .accessibilityElement(children: .contain)
            .accessibilityLabel("Anúncio")
            .onAppear { Anuncios.iniciar() }
        }
    }
}

private struct BannerDaAdMob: UIViewRepresentable {
    let idDoBanner: String
    let largura: CGFloat
    @Binding var altura: CGFloat

    func makeCoordinator() -> Coordenador { Coordenador(altura: $altura) }

    func makeUIView(context: Context) -> BannerView {
        let banner = BannerView()
        banner.adUnitID = idDoBanner
        banner.delegate = context.coordinator
        return banner
    }

    func updateUIView(_ banner: BannerView, context: Context) {
        // Um pedido por largura: girar o aparelho ou redimensionar a janela pede de novo.
        guard largura > 0, context.coordinator.larguraPedida != largura else { return }
        context.coordinator.larguraPedida = largura
        banner.adSize = largeAnchoredAdaptiveBanner(width: largura)
        banner.load(Anuncios.pedido())
    }

    @MainActor
    final class Coordenador: NSObject, @preconcurrency BannerViewDelegate {
        private let altura: Binding<CGFloat>
        var larguraPedida: CGFloat = 0

        init(altura: Binding<CGFloat>) {
            self.altura = altura
        }

        func bannerViewDidReceiveAd(_ banner: BannerView) {
            altura.wrappedValue = banner.adSize.size.height
        }

        func bannerView(_ banner: BannerView, didFailToReceiveAdWithError erro: Error) {
            altura.wrappedValue = 0
        }
    }
}

extension View {
    /// O banner no pe da aba, acima da barra de abas e abaixo do +, so quando a regra deixa.
    func comBannerDoGratuito(_ mostra: Bool) -> some View {
        safeAreaInset(edge: .bottom, spacing: 0) {
            if mostra { BannerDeAnuncio() }
        }
    }
}
