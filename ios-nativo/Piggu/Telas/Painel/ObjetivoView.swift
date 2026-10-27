import SwiftUI

/// Novo objetivo ou editar um (o formulario do objetivos.ts, numa folha).
struct ObjetivoView: View {
    let modelo: ModeloDoPainel
    let objetivo: ObjetivoDoCofrinho?

    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var nome = ""
    @State private var alvo = ""
    @State private var temPrazo = false
    @State private var prazo = Date()
    @State private var compartilhado = false
    @State private var salvando = false
    @State private var erro: String?

    /// So quem criou decide se e a dois.
    private var podeCompartilhar: Bool { modelo.temCompanhia && (objetivo.map { modelo.ehMeu($0.usuario) } ?? true) }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Nome", text: $nome, prompt: Text("Viagem de férias"))
                        .onChange(of: nome) { _, novo in if novo.count > 60 { nome = String(novo.prefix(60)) } }
                    LabeledContent("Quanto (\(Formatos.simboloDaMoeda))") {
                        CampoDeValor(rotulo: "Quanto", texto: $alvo).multilineTextAlignment(.trailing)
                    }
                    Toggle("Até quando (opcional)", isOn: $temPrazo)
                    if temPrazo {
                        DatePicker("Prazo", selection: $prazo, in: Date()..., displayedComponents: .date)
                    }
                    if podeCompartilhar {
                        Toggle("A dois: quem mora com você vê e deposita junto", isOn: $compartilhado)
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
            .navigationTitle(objetivo == nil ? "Novo objetivo" : "Editar objetivo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { fechar() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Salvar objetivo") { Task { await salvar() } }.disabled(salvando)
                }
            }
            .onAppear(perform: preencher)
        }
    }

    private func preencher() {
        guard let objetivo else { return }
        nome = objetivo.nome
        alvo = Formatos.valorParaDigitar(objetivo.alvo)
        if let data = objetivo.prazo.flatMap(Formatos.data(deDia:)) {
            temPrazo = true
            prazo = data
        }
        compartilhado = objetivo.compartilhado ?? false
    }

    private func salvar() async {
        let nome = nome.trimmingCharacters(in: .whitespaces)
        guard !nome.isEmpty else {
            erro = String(localized: "Dê um nome ao objetivo.")
            return
        }
        guard let valor = Formatos.lerValor(alvo), valor > 0 else {
            erro = String(localized: "Digite quanto quer juntar.")
            return
        }
        salvando = true
        defer { salvando = false }
        // So vai quando a pessoa pode escolher: ausente, o backend deixa como esta.
        let pedido = PedidoDeObjetivo(nome: nome, alvo: valor, prazo: temPrazo ? Formatos.dia(prazo) : nil,
                                      compartilhado: podeCompartilhar ? compartilhado : nil)
        do {
            try await modelo.salvarObjetivo(pedido, id: objetivo?.id)
            fechar()
        } catch {
            self.erro = error.localizedDescription
        }
    }
}
