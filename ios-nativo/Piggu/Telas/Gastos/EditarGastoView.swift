import SwiftUI

/// Editar um gasto: item, categoria, valor, data e, para quem lancou, se e compartilhado.
struct EditarGastoView: View {
    let gasto: Gasto
    @Binding var categorias: [String]
    /// So quem lancou volta o compartilhado para "só meu", e so faz sentido com mais gente na casa.
    let podeCompartilhar: Bool
    /// Do compartilhado, so quem lancou apaga ou transforma em receita.
    var podeApagar = false
    let aoSalvar: (Gasto) -> Void
    /// Fecha a folha e pede a confirmacao de apagar na lista (que sabe das parcelas).
    var aoApagar: () -> Void = {}
    var aoVirarReceita: () -> Void = {}

    @Environment(Sessao.self) private var sessao
    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var item = ""
    @State private var categoria = ""
    @State private var valor = ""
    @State private var compartilhado = false
    @State private var data = Date()
    @State private var salvando = false
    @State private var erro: String?
    @State private var confirmandoData = false
    @State private var confirmandoReceita = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Descrição", text: $item)
                        .onChange(of: item) { _, novo in if novo.count > 200 { item = String(novo.prefix(200)) } }
                    CampoDeCategoria(categoria: $categoria, categorias: $categorias, automatica: false)
                    LabeledContent("Valor (\(Formatos.simboloDaMoeda))") {
                        CampoDeValor(rotulo: "Valor", texto: $valor).multilineTextAlignment(.trailing)
                    }
                    DatePicker("Data", selection: $data, in: Limites.datas(), displayedComponents: .date)
                    if podeCompartilhar {
                        Toggle("Compartilhado", isOn: $compartilhado)
                    }
                }
                .listRowBackground(paleta.superficie)
                if podeApagar {
                    Section {
                        // Parcelado nao vira receita: o backend recusa (D-199).
                        if gasto.parcelas == nil {
                            Button("Era uma receita", systemImage: "arrow.left.arrow.right") { confirmandoReceita = true }
                                .disabled(salvando)
                        }
                        Button("Apagar gasto", systemImage: "trash", role: .destructive) {
                            fechar()
                            aoApagar()
                        }
                        .disabled(salvando)
                    }
                    .listRowBackground(paleta.superficie)
                }
                if let erro {
                    Section { Text(erro).foregroundStyle(paleta.perigo) }
                        .listRowBackground(paleta.perigoSuave)
                }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .navigationTitle("Editar gasto")
            .tituloDaFolha()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { fechar() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Salvar") { Task { await salvar() } }.disabled(salvando)
                }
            }
            .confirmacaoDeData(data, mostrando: $confirmandoData) { Task { await salvar(dataConfirmada: true) } }
            .confirmationDialog("Transformar \"\(gasto.item)\" em receita?", isPresented: $confirmandoReceita,
                                titleVisibility: .visible) {
                Button("Transformar em receita") { Task { await virarReceita() } }
            } message: {
                Text("O gasto sai da lista e entra nas receitas com a mesma data e o mesmo valor.")
            }
            .onAppear {
                item = gasto.item
                categoria = gasto.categoria
                valor = Formatos.valorParaDigitar(gasto.valor)
                compartilhado = gasto.compartilhado ?? false
                data = Formatos.data(deDia: gasto.data) ?? Date()
            }
        }
    }

    private func virarReceita() async {
        salvando = true
        defer { salvando = false }
        do {
            try await sessao.cliente.virarReceita(gasto.id)
            aoVirarReceita()
            fechar()
        } catch {
            self.erro = error.localizedDescription
        }
    }

    private func salvar(dataConfirmada: Bool = false) async {
        let item = item.trimmingCharacters(in: .whitespaces)
        let dia = Formatos.dia(data)
        guard !item.isEmpty else {
            erro = String(localized: "Digite a descrição.")
            return
        }
        let valor = Formatos.lerValor(valor)
        if let problema = Limites.problema(valor) {
            erro = problema
            return
        }
        guard let valor else { return }
        // So pergunta se a data mudou: o gasto antigo que a pessoa so corrige o valor passa direto.
        if !dataConfirmada, dia != gasto.data, Limites.pedeConfirmacao(data) {
            confirmandoData = true
            return
        }
        salvando = true
        defer { salvando = false }
        do {
            let salvo = try await sessao.cliente.editarGasto(gasto.id, EdicaoDeGasto(
                item: item, categoria: categoria, valor: valor, compartilhado: podeCompartilhar ? compartilhado : nil,
                data: dia == gasto.data ? nil : dia))
            aoSalvar(salvo)
            fechar()
        } catch {
            self.erro = error.localizedDescription
        }
    }
}
