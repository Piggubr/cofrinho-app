import CoreText
import SwiftUI
import UIKit

/// A Sora do site, embutida no app: o mesmo arquivo do pacote @fontsource-variable/sora
/// (Recursos/Sora.woff2, licenca OFL em Recursos/Sora-OFL.txt). E variavel, do peso 100 ao
/// 800; o CoreText le o WOFF2 direto.
enum Fonte {
    static let familia = "Sora"

    /// Chamado uma vez, ao abrir o app. Sem o arquivo, o app segue com a fonte do sistema.
    @MainActor
    static func registrar() {
        guard let url = Bundle.main.url(forResource: familia, withExtension: "woff2") else { return }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)

        // Titulos da barra de navegacao, que sao do UIKit, tambem na Sora.
        let barra = UINavigationBar.appearance()
        barra.largeTitleTextAttributes = [.font: uiFont(.largeTitle, tamanho: 34, peso: .bold)]
        barra.titleTextAttributes = [.font: uiFont(.headline, tamanho: 17, peso: .semibold)]
    }

    /// Tamanho de cada estilo no tamanho de texto padrao, como os do sistema.
    static func tamanho(_ estilo: Font.TextStyle) -> CGFloat {
        switch estilo {
        case .largeTitle: 34
        case .title: 28
        case .title2: 22
        case .title3: 20
        case .headline, .body: 17
        case .callout: 16
        case .subheadline: 15
        case .footnote: 13
        case .caption: 12
        case .caption2: 11
        default: 17
        }
    }

    /// shortcut: o tamanho vale o do Dynamic Type ao abrir o app; trocar com o app aberto so
    /// muda os titulos da barra na proxima abertura. Upgrade se alguem reclamar.
    private static func uiFont(_ estilo: UIFont.TextStyle, tamanho: CGFloat, peso: UIFont.Weight) -> UIFont {
        let descritor = UIFontDescriptor(fontAttributes: [
            .family: familia,
            .traits: [UIFontDescriptor.TraitKey.weight: peso],
        ])
        return UIFontMetrics(forTextStyle: estilo).scaledFont(for: UIFont(descriptor: descritor, size: tamanho))
    }
}

extension Font {
    /// Sora no tamanho do estilo, acompanhando o Dynamic Type.
    static func sora(_ estilo: Font.TextStyle = .body, peso: Font.Weight = .regular) -> Font {
        .custom(Fonte.familia, size: Fonte.tamanho(estilo), relativeTo: estilo).weight(peso)
    }
}
