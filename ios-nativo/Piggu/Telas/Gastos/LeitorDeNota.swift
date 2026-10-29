import SwiftUI
import VisionKit

/// A camera lendo o cupom (DataScannerViewController): mostra o total e a data que achou e so
/// preenche o lancamento quando a pessoa toca em usar. So aparece onde o aparelho le texto.
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
                    Button("Usar este valor") {
                        aoLer(leitura)
                        fechar()
                    }
                    .botaoPrincipal()
                } else {
                    Text("Aponte para a parte do cupom com o total.").font(.sora(.subheadline))
                }
                Button("Cancelar") { fechar() }.font(.sora(.subheadline))
            }
            .padding(18)
            .frame(maxWidth: .infinity)
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

            /// Mantem o que ja achou: a camera tremendo nao apaga um total lido.
            private func ler(_ itens: [RecognizedItem]) {
                let linhas = itens.compactMap { item -> String? in
                    if case let .text(texto) = item { return texto.transcript }
                    return nil
                }
                let nova = LeitorDeCupom.ler(linhas.flatMap { $0.components(separatedBy: .newlines) })
                leitura = LeitorDeCupom.Leitura(total: nova.total ?? leitura.total, data: nova.data ?? leitura.data)
            }
        }
    }
}
