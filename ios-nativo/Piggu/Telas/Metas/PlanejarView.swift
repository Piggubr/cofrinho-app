import SwiftUI

/// Planejar (features/plan-hub): o que olha para a frente (quanto gastar e o que vence) num lugar so.
struct PlanejarView: View {
    @Environment(\.paleta) private var paleta

    var body: some View {
        List {
            Section {
                atalho(.orcamento, descricao: "O limite do mês e, por categoria, com aviso em 80% e 100%.")
                atalho(.contasDoMes, descricao: "Aluguel, luz, internet: o que vence todo mês, lançado no dia.")
            }
            .listRowBackground(paleta.superficie)
        }
        .scrollContentBackground(.hidden)
        .background(paleta.fundo)
        .navigationTitle("Planejar")
    }

    private func atalho(_ tela: TelaDoMais, descricao: LocalizedStringKey) -> some View {
        NavigationLink(value: tela) {
            LadoALado(espaco: 12) {
                Image(systemName: tela.simbolo)
                    .frame(width: 38, height: 38)
                    .foregroundStyle(paleta.primaria)
                    .background(paleta.suave, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(tela.titulo).font(.sora(.body, peso: .semibold))
                    Text(descricao).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                }
            }
            .padding(.vertical, 4)
        }
    }
}
