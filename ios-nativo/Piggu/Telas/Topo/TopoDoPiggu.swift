import SwiftUI

/// Abre uma aba ou tela de dentro do app (busca, assistente). Quem sabe abrir e a PrincipalView.
struct AbrirRota: Sendable {
    let acao: @MainActor @Sendable (Rota) -> Void

    @MainActor func callAsFunction(_ rota: Rota) { acao(rota) }
}

extension EnvironmentValues {
    @Entry var abrirRota = AbrirRota { _ in }
}

extension View {
    /// O cabecalho do site (shell.html) no topo de cada aba: a marca a esquerda, que volta ao
    /// Painel, e a busca e o assistente a direita.
    func topoDoPiggu() -> some View {
        modifier(TopoDoPiggu())
    }
}

private struct TopoDoPiggu: ViewModifier {
    @Environment(\.abrirRota) private var abrirRota
    @State private var buscando = false
    @State private var perguntando = false

    func body(content: Content) -> some View {
        content
            .toolbar {
                if #available(iOS 26, *) {
                    // A bolha da barra fica de fora: o logo traz a dele, do tamanho do logo.
                    ToolbarItem(placement: .topBarLeading) { marca }
                        .sharedBackgroundVisibility(.hidden)
                } else {
                    ToolbarItem(placement: .topBarLeading) { marca }
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { buscando = true } label: {
                        Label("Buscar", systemImage: "magnifyingglass")
                    }
                    Button { perguntando = true } label: {
                        Label("Perguntar ao assistente", systemImage: "sparkles")
                    }
                }
            }
            .sheet(isPresented: $buscando) { BuscaView() }
            .sheet(isPresented: $perguntando) { AssistenteView() }
    }

    /// O logo ocupa a bolha de vidro inteira (por isso uma view com toque, e nao um Button, que a
    /// barra desenharia como icone dentro da bolha dela).
    private var marca: some View {
        Image("Logo")
            .resizable()
            .scaledToFill()
            .frame(width: 44, height: 44)
            .clipShape(Circle())
            .bolhaDeVidro()
            .contentShape(Circle())
            .onTapGesture { abrirRota(.aba(.painel)) }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Piggu")
            .accessibilityHint("Abre o Painel")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { abrirRota(.aba(.painel)) }
    }
}

private extension View {
    @ViewBuilder
    func bolhaDeVidro() -> some View {
        if #available(iOS 26, *) {
            glassEffect(.regular.interactive(), in: Circle())
        } else {
            self
        }
    }
}
