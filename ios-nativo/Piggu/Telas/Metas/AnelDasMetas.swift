import SwiftUI

/// O anel das Minhas metas (anel-das-metas.ts): gomos que enchem ate o percentual, o porquinho
/// flutuando em cima e o numero no meio. O anel anima ao aparecer e quando o numero muda.
struct AnelDasMetas: View {
    /// 0 a 100.
    let percentual: Int
    var pequeno = false

    @State private var mostrado: Double = 0
    @State private var flutuando = false
    @Environment(\.accessibilityReduceMotion) private var reduzirMovimento
    @Environment(\.paleta) private var paleta

    private var lado: CGFloat { pequeno ? 132 : 176 }
    private var espessura: CGFloat { pequeno ? 16 : 22 }
    private static let gomos = 8

    var body: some View {
        ZStack {
            ForEach(0..<Self.gomos, id: \.self) { indice in
                gomo(indice)
            }
            VStack(spacing: 0) {
                Text("\(percentual)%").font(.sora(pequeno ? .title3 : .title, peso: .bold)).monospacedDigit()
                Text("concluído").font(.sora(.caption)).foregroundStyle(paleta.textoSuave)
            }
            Image("PigguLove")
                .resizable()
                .scaledToFit()
                .frame(width: lado * 0.4)
                .rotationEffect(.degrees(flutuando ? 3 : -3))
                .offset(y: -lado / 2 - (flutuando ? 6 : 0))
                .shadow(color: .black.opacity(0.12), radius: 4, y: 4)
        }
        .frame(width: lado, height: lado)
        // O porquinho sobe acima do anel: a folga e do tamanho dele.
        .padding(.top, lado * 0.28)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Progresso das metas do mês")
        .accessibilityValue("\(percentual)%")
        .onAppear {
            withAnimation(reduzirMovimento ? nil : .easeOut(duration: 0.9)) { mostrado = Double(percentual) }
            guard !reduzirMovimento else { return }
            withAnimation(.easeInOut(duration: 1.6).repeatForever(autoreverses: true)) { flutuando = true }
        }
        .onChange(of: percentual) { _, novo in
            withAnimation(reduzirMovimento ? nil : .smooth) { mostrado = Double(novo) }
        }
    }

    /// Cada gomo e 1/8 do anel com um corte de 3 graus; enche pela fracao do percentual que cai nele.
    private func gomo(_ indice: Int) -> some View {
        let tamanho = 1.0 / Double(Self.gomos)
        let inicio = Double(indice) * tamanho
        let corte = 3.0 / 360
        let cheio = min(max(mostrado / 100 - inicio, 0), tamanho - corte)
        return ZStack {
            Circle().trim(from: inicio, to: inicio + tamanho - corte)
                .stroke(paleta.trilho, style: StrokeStyle(lineWidth: espessura))
            Circle().trim(from: inicio, to: inicio + cheio)
                .stroke(paleta.primaria, style: StrokeStyle(lineWidth: espessura))
        }
        .rotationEffect(.degrees(-90))
        .padding(8 + espessura / 2)
    }
}
