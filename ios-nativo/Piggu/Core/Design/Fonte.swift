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
        // O titulo grande da barra nao quebra linha: acima de 36 pt "Contas do mês" e "Cartões e
        // bancos" viravam "Contas do…" no texto grande (IOS-5). O conteudo segue crescendo.
        barra.largeTitleTextAttributes = [.font: uiFont(.largeTitle, tamanho: 34, peso: .bold, maximo: 36)]
        barra.titleTextAttributes = [.font: uiFont(.headline, tamanho: 17, peso: .semibold, maximo: 24)]
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
    private static func uiFont(_ estilo: UIFont.TextStyle, tamanho: CGFloat, peso: UIFont.Weight, maximo: CGFloat) -> UIFont {
        let descritor = UIFontDescriptor(fontAttributes: [
            .family: familia,
            .traits: [UIFontDescriptor.TraitKey.weight: peso],
        ])
        return UIFontMetrics(forTextStyle: estilo).scaledFont(for: UIFont(descriptor: descritor, size: tamanho),
                                                               maximumPointSize: maximo)
    }
}

extension View {
    /// Titulo das folhas: em linha no texto padrao; no texto grande, em cima, sozinho na linha,
    /// para "Lançar gasto" nao virar "Lançar…" entre Cancelar e Lançar (IOS-5).
    func tituloDaFolha() -> some View { modifier(TituloDaFolha()) }
}

extension View {
    /// Segmentado no texto padrao; no texto grande vira menu, que cresce com a letra: o
    /// UISegmentedControl fica do mesmo tamanho e corta as opcoes (IOS-5).
    func seletorSegmentado() -> some View { modifier(SeletorSegmentado()) }
}

private struct SeletorSegmentado: ViewModifier {
    @Environment(\.dynamicTypeSize) private var tamanho

    func body(content: Content) -> some View {
        if tamanho.isAccessibilitySize {
            content.pickerStyle(.menu)
        } else {
            content.pickerStyle(.segmented)
        }
    }
}

private struct TituloDaFolha: ViewModifier {
    @Environment(\.dynamicTypeSize) private var tamanho

    func body(content: Content) -> some View {
        content.navigationBarTitleDisplayMode(tamanho.isAccessibilitySize ? .large : .inline)
    }
}

extension Font {
    /// Sora no tamanho do estilo, acompanhando o Dynamic Type.
    static func sora(_ estilo: Font.TextStyle = .body, peso: Font.Weight = .regular) -> Font {
        .custom(Fonte.familia, size: Fonte.tamanho(estilo), relativeTo: estilo).weight(peso)
    }
}
