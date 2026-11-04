import SwiftUI

/// Planejar (features/plan-hub): o que olha para a frente (quanto gastar e o que vence) num lugar so.
struct PlanejarView: View {
    /// Na aba Planejar (D-164), abre os Relatórios; no Mais, fica sem eles.
    var abrirRelatorios: (() -> Void)?

    @Environment(\.paleta) private var paleta

    var body: some View {
        List {
            Section {
                atalho(.orcamento, descricao: "O limite do mês e, por categoria, com aviso em 80% e 100%.")
                atalho(.contasDoMes, descricao: "Aluguel, luz, internet: o que vence todo mês, lançado no dia.")
                if let abrirRelatorios {
                    Button(action: abrirRelatorios) {
                        linha(titulo: String(localized: "Relatórios"), simbolo: "chart.bar.xaxis",
                              descricao: "Para onde o dinheiro foi, mês a mês e por categoria.")
                    }
                    .foregroundStyle(.primary)
                }
                atalho(.metas, descricao: "O que você quer alcançar este mês, passo a passo.")
            }
            .listRowBackground(paleta.superficie)
        }
        .scrollContentBackground(.hidden)
        .background(paleta.fundo)
        .navigationTitle("Planejar")
    }

    private func atalho(_ tela: TelaDoMais, descricao: LocalizedStringKey) -> some View {
        NavigationLink(value: tela) { linha(titulo: tela.titulo, simbolo: tela.simbolo, descricao: descricao) }
    }

    private func linha(titulo: String, simbolo: String, descricao: LocalizedStringKey) -> some View {
        LadoALado(espaco: 12) {
            Image(systemName: simbolo)
                .frame(width: 38, height: 38)
                .foregroundStyle(paleta.primaria)
                .background(paleta.suave, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(titulo).font(.sora(.body, peso: .semibold))
                Text(descricao).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            }
        }
        .padding(.vertical, 4)
    }
}
