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
    @Environment(\.paleta) private var paleta
    @State private var buscando = false
    @State private var perguntando = false

    func body(content: Content) -> some View {
        content
            .toolbar {
                if #available(iOS 26, *) {
                    // Sem a bolha de vidro: a marca e o titulo da barra, nao um botao.
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

    /// Uma view com toque, e nao um Button: na barra do iOS 26 o botao mostraria so o icone.
    private var marca: some View {
        HStack(spacing: 8) {
            Image("Logo")
                .resizable()
                .scaledToFit()
                .frame(width: 28, height: 28)
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            Text(verbatim: "Piggu")
                .font(.sora(.headline, peso: .bold))
                .foregroundStyle(paleta.primaria)
                .fixedSize()
        }
        .contentShape(Rectangle())
        .onTapGesture { abrirRota(.aba(.painel)) }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Piggu")
        .accessibilityHint("Abre o Painel")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { abrirRota(.aba(.painel)) }
    }
}
