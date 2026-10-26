import SwiftUI

/// Setas e o nome do mes, em vidro, flutuando no topo da lista. No mes atual a seta de
/// avancar some, e o vidro anima a troca.
struct SeletorDeMes: View {
    @Binding var mes: Date

    @Namespace private var vidro
    @Environment(\.paleta) private var paleta

    private var ehMesAtual: Bool { Formatos.mes(mes) == Formatos.mes(Date()) }

    var body: some View {
        GrupoDeVidro(espaco: 6) {
            seta("chevron.left", rotulo: "Mês anterior", passo: -1)
                .pigguVidro(em: Circle(), id: "anterior", namespace: vidro)
            Text(Formatos.inicialMaiuscula(Formatos.nomeDoMes(mes)))
                .font(.sora(.headline, peso: .semibold))
                .contentTransition(.numericText())
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .padding(.horizontal, 18)
                .frame(minHeight: 44)
                .pigguVidro(id: "mes", namespace: vidro)
                .accessibilityAddTraits(.isHeader)
            if ehMesAtual {
                // Guarda o lugar da seta: o nome do mes fica no centro.
                Color.clear.frame(width: 44, height: 44)
            } else {
                seta("chevron.right", rotulo: "Próximo mês", passo: 1)
                    .pigguVidro(em: Circle(), id: "proximo", namespace: vidro)
            }
        }
        .animation(.smooth, value: mes)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity)
    }

    private func seta(_ simbolo: String, rotulo: LocalizedStringKey, passo: Int) -> some View {
        Button {
            mes = Formatos.somarMeses(passo, a: mes)
        } label: {
            Image(systemName: simbolo)
                .font(.body.weight(.semibold))
                .foregroundStyle(paleta.primaria)
                .frame(width: 44, height: 44)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(rotulo)
    }
}

/// Barra horizontal de um valor contra o maior, com a marca opcional do mes anterior.
struct BarraDeValor: View {
    /// De 0 a 1.
    let fracao: Double
    var marca: Double?

    @Environment(\.paleta) private var paleta

    var body: some View {
        Capsule()
            .fill(paleta.trilho)
            .frame(height: 8)
            .overlay(alignment: .leading) {
                GeometryReader { espaco in
                    Capsule()
                        .fill(paleta.serieGasto)
                        .frame(width: max(8, espaco.size.width * min(max(fracao, 0), 1)))
                    if let marca {
                        Capsule()
                            .fill(paleta.texto)
                            .frame(width: 3, height: 16)
                            .offset(x: espaco.size.width * min(max(marca, 0), 1) - 1.5, y: -4)
                    }
                }
            }
            .padding(.vertical, 4)
            .accessibilityHidden(true)
    }
}

extension Decimal {
    /// Para desenhar: barras e proporcoes, nunca para conta de dinheiro.
    var paraDesenho: Double { NSDecimalNumber(decimal: self).doubleValue }
}
