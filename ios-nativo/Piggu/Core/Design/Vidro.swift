import SwiftUI

// Liquid Glass so em controles que flutuam sobre o conteudo (o +, o seletor de mes, filtros),
// nunca em cartoes ou listas. No iOS 17 e 18 o vidro vira o material translucido do sistema;
// com Reduzir Transparencia, um fundo solido com borda.

extension View {
    /// Fundo de vidro num controle flutuante. Dentro de um `GrupoDeVidro`, o `id` deixa o
    /// SwiftUI animar a troca entre um controle e outro.
    func pigguVidro<Forma: Shape>(
        em forma: Forma = Capsule(),
        id: String? = nil,
        namespace: Namespace.ID? = nil
    ) -> some View {
        modifier(Vidro(forma: forma, id: id, namespace: namespace))
    }

    /// Botao principal da tela: vidro proeminente na cor do tema.
    func botaoPrincipal() -> some View {
        modifier(BotaoPrincipal())
    }
}

private struct Vidro<Forma: Shape>: ViewModifier {
    let forma: Forma
    let id: String?
    let namespace: Namespace.ID?

    @Environment(\.accessibilityReduceTransparency) private var reduzirTransparencia
    @Environment(\.paleta) private var paleta

    func body(content: Content) -> some View {
        if reduzirTransparencia {
            content
                .background(paleta.superficie, in: forma)
                .overlay(forma.stroke(paleta.borda))
        } else if #available(iOS 26, *) {
            if let id, let namespace {
                content
                    .glassEffect(.regular.interactive(), in: forma)
                    .glassEffectID(id, in: namespace)
            } else {
                content.glassEffect(.regular.interactive(), in: forma)
            }
        } else {
            content
                .background(.ultraThinMaterial, in: forma)
                .overlay(forma.stroke(.white.opacity(0.25)))
                .shadow(color: .black.opacity(0.12), radius: 12, y: 4)
        }
    }
}

/// O texto vai na --primaria-contraste: no escuro a primaria e clara e pede texto escuro.
private struct BotaoPrincipal: ViewModifier {
    @Environment(\.paleta) private var paleta

    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content.buttonStyle(.glassProminent).foregroundStyle(paleta.primariaContraste)
        } else {
            content.buttonStyle(.borderedProminent).foregroundStyle(paleta.primariaContraste)
        }
    }
}

/// Grupo de controles de vidro (seletor de mes, filtros): no iOS 26 eles se fundem quando
/// ficam perto e animam juntos; antes, so um HStack.
struct GrupoDeVidro<Conteudo: View>: View {
    var espaco: CGFloat = 8
    @ViewBuilder let conteudo: Conteudo

    var body: some View {
        if #available(iOS 26, *) {
            GlassEffectContainer(spacing: espaco) {
                HStack(spacing: espaco) { conteudo }
            }
        } else {
            HStack(spacing: espaco) { conteudo }
        }
    }
}

/// O + de lancar, flutuando no canto da tela sobre o conteudo: abre as opcoes, como o balao do
/// site (shell.html).
struct BotaoDeLancar: View {
    let gasto: () -> Void
    let receita: () -> Void

    @Environment(\.paleta) private var paleta

    var body: some View {
        Group {
            if #available(iOS 26, *) {
                // O glassProminent ja poe a margem dele em volta: 40 + margem da os 56 do outro.
                Menu { opcoes } label: { simbolo.frame(width: 40, height: 40) }
                    .buttonStyle(.glassProminent)
                    .foregroundStyle(paleta.primariaContraste)
                    .buttonBorderShape(.circle)
            } else {
                Menu { opcoes } label: {
                    simbolo
                        .frame(width: 56, height: 56)
                        .foregroundStyle(paleta.primariaContraste)
                        .background(paleta.primaria, in: Circle())
                        .shadow(color: paleta.primaria.opacity(0.35), radius: 12, y: 6)
                }
                .buttonStyle(.plain)
            }
        }
        .accessibilityLabel("Lançar")
        .padding(20)
    }

    @ViewBuilder
    private var opcoes: some View {
        Button(action: gasto) { Label("Lançar gasto", systemImage: "square.and.pencil") }
        Button(action: receita) { Label("Lançar receita", systemImage: "arrow.down.circle") }
    }

    private var simbolo: some View {
        Image(systemName: "plus")
            .font(.title2.weight(.semibold))
            .contentShape(Circle())
    }
}
