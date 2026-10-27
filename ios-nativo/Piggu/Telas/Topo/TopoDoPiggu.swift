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
    /// Painel, e a busca e o assistente a direita. `antes` entra na mesma bolha, a esquerda da
    /// lupa (a cotacao no Painel).
    func topoDoPiggu(@ViewBuilder antes: () -> some View = { EmptyView() }) -> some View {
        modifier(TopoDoPiggu(antes: antes()))
    }
}

private struct TopoDoPiggu<Antes: View>: ViewModifier {
    let antes: Antes

    @Environment(\.abrirRota) private var abrirRota
    @State private var buscando = false
    @State private var perguntando = false
    /// O icone que a pessoa escolheu no Perfil. O sistema nao avisa a troca: relido a cada vez
    /// que a aba aparece (voltar do Perfil, trocar de aba).
    @State private var icone = IconeDoApp.atual

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
                // Um item so, e nao um grupo: o grupo poe a folga dele entre cada botao.
                ToolbarItem(placement: .topBarTrailing) {
                    HStack(spacing: 0) {
                        antes.padding(.horizontal, 8)
                        Button { buscando = true } label: {
                            Label("Buscar", systemImage: "magnifyingglass").icone()
                        }
                        Button { perguntando = true } label: {
                            Label("Perguntar ao assistente", systemImage: "sparkles").icone()
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .sheet(isPresented: $buscando) { BuscaView() }
            .sheet(isPresented: $perguntando) { AssistenteView() }
            .onAppear { icone = IconeDoApp.atual }
    }

    /// O icone do app ocupa a bolha de vidro inteira (por isso uma view com toque, e nao um Button, que a
    /// barra desenharia como icone dentro da bolha dela).
    private var marca: some View {
        icone.previa
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

private extension Label where Title == Text, Icon == Image {
    /// So o simbolo, na cor do Piggu, com a area de toque de 44 pt: o mesmo passo entre os dois.
    func icone() -> some View {
        labelStyle(.iconOnly)
            .font(.system(size: 19, weight: .medium))
            .foregroundStyle(.tint)
            .frame(width: 40, height: 44)
            .contentShape(Rectangle())
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
