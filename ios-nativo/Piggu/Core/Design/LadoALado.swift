import SwiftUI

/// Lado a lado no texto comum; um embaixo do outro nos tamanhos de acessibilidade, para o texto
/// grande nao quebrar no meio da palavra nem do valor.
struct LadoALado<Conteudo: View>: View {
    var alinhamento: VerticalAlignment = .center
    var espaco: CGFloat?
    @ViewBuilder let conteudo: Conteudo

    @Environment(\.dynamicTypeSize) private var tamanho

    var body: some View {
        let layout = tamanho.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: espaco))
            : AnyLayout(HStackLayout(alignment: alinhamento, spacing: espaco))
        layout { conteudo }
    }
}
