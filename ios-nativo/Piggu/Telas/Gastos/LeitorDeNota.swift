import SwiftUI
import Vision
import VisionKit

/// A camera lendo o cupom (DataScannerViewController): mostra o total, a data e os itens que achou
/// e so preenche o lancamento quando a pessoa escolhe entre usar o total ou separar os itens.
/// So aparece onde o aparelho le texto.
struct LeitorDeNota: View {
    let aoLer: (LeitorDeCupom.Leitura) -> Void

    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var leitura = LeitorDeCupom.Leitura()

    static var disponivel: Bool {
        DataScannerViewController.isSupported && DataScannerViewController.isAvailable
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            Camera(leitura: $leitura).ignoresSafeArea()
            VStack(spacing: 10) {
                if let total = leitura.total {
                    Text("Total \(Formatos.moeda(total))\(leitura.data.map { " · " + Formatos.dataCurta($0) } ?? "")")
                        .font(.sora(.headline, peso: .semibold)).monospacedDigit()
                    Button("Usar o total") {
                        aoLer(LeitorDeCupom.Leitura(total: total, data: leitura.data))
                        fechar()
                    }
                    .botaoPrincipal()
                } else if leitura.itens.isEmpty {
                    Text("Aponte para a parte do cupom com o total.").font(.sora(.subheadline))
                }
                if !leitura.itens.isEmpty {
                    let soma = leitura.itens.reduce(Decimal(0)) { $0 + $1.valor }
                    Text("\(leitura.itens.count) itens, somando \(Formatos.moeda(soma))")
                        .font(.sora(.subheadline)).monospacedDigit()
                    Button("Separar os itens") {
                        aoLer(leitura)
                        fechar()
                    }
                    .font(.sora(.headline, peso: .semibold))
                }
                Button("Cancelar") { fechar() }.font(.sora(.subheadline))
            }
            .padding(18)
            // Deitado, o cartao nao atravessa a tela toda e deixa mais cupom a vista.
            .frame(maxWidth: 520)
            .pigguVidro(em: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .padding(16)
        }
    }

    private struct Camera: UIViewControllerRepresentable {
        @Binding var leitura: LeitorDeCupom.Leitura

        func makeUIViewController(context: Context) -> DataScannerViewController {
            let camera = DataScannerViewController(
                recognizedDataTypes: [.text(languages: ["pt-BR"])], qualityLevel: .accurate,
                recognizesMultipleItems: true, isHighlightingEnabled: true)
            camera.delegate = context.coordinator
            try? camera.startScanning()
            return camera
        }

        func updateUIViewController(_: DataScannerViewController, context _: Context) {}

        func makeCoordinator() -> Coordenador { Coordenador(leitura: $leitura) }

        @MainActor
        final class Coordenador: NSObject, DataScannerViewControllerDelegate {
            @Binding var leitura: LeitorDeCupom.Leitura

            init(leitura: Binding<LeitorDeCupom.Leitura>) {
                _leitura = leitura
            }

            func dataScanner(_: DataScannerViewController, didUpdate _: [RecognizedItem], allItems: [RecognizedItem]) {
                ler(allItems)
            }

            func dataScanner(_: DataScannerViewController, didAdd _: [RecognizedItem], allItems: [RecognizedItem]) {
                ler(allItems)
            }

            /// Mantem o que ja achou: a camera tremendo nao apaga um total lido, e a lista de itens
            /// so troca por uma com pelo menos tantos itens quanto a de antes.
            private func ler(_ itens: [RecognizedItem]) {
                let trechos = itens.flatMap { item -> [LeitorDeCupom.Trecho] in
                    if case let .text(texto) = item { return Self.trechos(texto.observation) }
                    return []
                }
                let nova = LeitorDeCupom.ler(trechos)
                leitura = LeitorDeCupom.Leitura(total: nova.total ?? leitura.total, data: nova.data ?? leitura.data,
                                                itens: nova.itens.count >= leitura.itens.count ? nova.itens : leitura.itens)
            }

            /// Uma linha por trecho, com a caixa dela: o bloco da camera junta varias linhas de uma
            /// coluna. Sem a caixa da linha, vai o bloco inteiro (o leitor divide a altura).
            private static func trechos(_ observacao: VNRecognizedTextObservation) -> [LeitorDeCupom.Trecho] {
                guard let candidato = observacao.topCandidates(1).first else { return [] }
                let texto = candidato.string
                var trechos: [LeitorDeCupom.Trecho] = []
                var falhou = false
                texto.enumerateSubstrings(in: texto.startIndex..., options: .byLines) { linha, faixa, _, parar in
                    guard let linha, !linha.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                    guard let caixa = try? candidato.boundingBox(for: faixa) else {
                        falhou = true
                        parar = true
                        return
                    }
                    trechos.append(.init(texto: linha, cantos: [caixa.topLeft, caixa.topRight, caixa.bottomRight, caixa.bottomLeft]))
                }
                if falhou || trechos.isEmpty {
                    return [.init(texto: texto, cantos: [observacao.topLeft, observacao.topRight, observacao.bottomRight, observacao.bottomLeft])]
                }
                return trechos
            }
        }
    }
}
