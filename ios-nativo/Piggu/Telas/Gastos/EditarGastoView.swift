import SwiftUI

/// Editar um gasto: item, categoria, valor e, para quem lancou, se e compartilhado.
struct EditarGastoView: View {
    let gasto: Gasto
    @Binding var categorias: [String]
    /// So quem lancou volta o compartilhado para "só meu", e so faz sentido com mais gente na casa.
    let podeCompartilhar: Bool
    let aoSalvar: (Gasto) -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var item = ""
    @State private var categoria = ""
    @State private var valor = ""
    @State private var compartilhado = false
    @State private var salvando = false
    @State private var erro: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Item", text: $item)
                        .onChange(of: item) { _, novo in if novo.count > 200 { item = String(novo.prefix(200)) } }
                    CampoDeCategoria(categoria: $categoria, categorias: $categorias, automatica: false)
                    LabeledContent("Valor (\(Formatos.simboloDaMoeda))") {
                        CampoDeValor(rotulo: "Valor", texto: $valor).multilineTextAlignment(.trailing)
                    }
                    if podeCompartilhar {
                        Toggle("Compartilhado", isOn: $compartilhado)
                    }
                }
                .listRowBackground(paleta.superficie)
                if let erro {
                    Section { Text(erro).foregroundStyle(paleta.perigo) }
                        .listRowBackground(paleta.perigoSuave)
                }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .navigationTitle("Editar gasto")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { fechar() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Salvar") { Task { await salvar() } }.disabled(salvando)
                }
            }
            .onAppear {
                item = gasto.item
                categoria = gasto.categoria
                valor = Formatos.valorParaDigitar(gasto.valor)
                compartilhado = gasto.compartilhado ?? false
            }
        }
    }

    private func salvar() async {
        let item = item.trimmingCharacters(in: .whitespaces)
        guard !item.isEmpty, let valor = Formatos.lerValor(valor), valor >= 0 else {
            erro = String(localized: "Confira o nome e o valor do gasto.")
            return
        }
        salvando = true
        defer { salvando = false }
        do {
            let salvo = try await sessao.cliente.editarGasto(gasto.id, EdicaoDeGasto(
                item: item, categoria: categoria, valor: valor, compartilhado: podeCompartilhar ? compartilhado : nil))
            aoSalvar(salvo)
            fechar()
        } catch {
            self.erro = error.localizedDescription
        }
    }
}
