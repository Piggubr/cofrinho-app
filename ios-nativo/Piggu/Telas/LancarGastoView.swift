import SwiftUI

/// Lanca um gasto a vista com um item (POST /expenses).
struct LancarGastoView: View {
    /// Chamado depois que o backend aceitou o lancamento.
    let aoSalvar: () -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(\.dismiss) private var fechar

    @State private var item = ""
    @State private var valorDigitado = ""
    @State private var categoria = ""
    @State private var categorias: [String] = []
    @State private var data = Date()
    @State private var estabelecimento = ""
    @State private var salvando = false
    @State private var erro: String?

    /// O mesmo teto do backend (ExpenseItemRequest).
    private static let valorMaximo: Decimal = 1_000_000

    private var valor: Decimal? { Formatos.lerValor(valorDigitado) }

    private var problemaNoValor: String? {
        guard !valorDigitado.isEmpty else { return nil }
        guard let valor else { return "Valor inválido. Use, por exemplo, 12,50." }
        if valor <= 0 { return "O valor precisa ser maior que zero." }
        if valor > Self.valorMaximo { return "O valor é alto demais." }
        return nil
    }

    private var podeSalvar: Bool {
        !item.trimmingCharacters(in: .whitespaces).isEmpty && valor != nil && problemaNoValor == nil && !salvando
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("O que foi? Ex.: Almoço", text: $item)
                        .textInputAutocapitalization(.sentences)
                    HStack {
                        Text("R$").foregroundStyle(.secondary)
                        TextField("0,00", text: $valorDigitado)
                            .keyboardType(.decimalPad)
                            .font(.title3.monospacedDigit())
                    }
                } footer: {
                    if let problemaNoValor {
                        Text(problemaNoValor).foregroundStyle(.red)
                    }
                }

                Section {
                    Picker("Categoria", selection: $categoria) {
                        if categorias.isEmpty {
                            Text("Carregando…").tag("")
                        }
                        ForEach(categorias, id: \.self) { Text($0).tag($0) }
                    }
                    DatePicker("Data", selection: $data, in: ...Date(), displayedComponents: .date)
                    TextField("Onde? (opcional)", text: $estabelecimento)
                }

                if let erro {
                    Section {
                        Text(erro).foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Lançar gasto")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { fechar() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if salvando {
                        ProgressView()
                    } else {
                        Button("Salvar") { Task { await salvar() } }
                            .disabled(!podeSalvar)
                    }
                }
            }
            .task { await carregarCategorias() }
        }
    }

    private func carregarCategorias() async {
        do {
            categorias = try await sessao.cliente.listarCategorias()
            if categoria.isEmpty || !categorias.contains(categoria) {
                categoria = categorias.contains("Outros") ? "Outros" : categorias.first ?? ""
            }
        } catch {
            erro = error.localizedDescription
        }
    }

    private func salvar() async {
        guard podeSalvar, let valor else { return }
        salvando = true
        erro = nil
        defer { salvando = false }

        let onde = estabelecimento.trimmingCharacters(in: .whitespaces)
        let pedido = NovoLancamento(
            data: Formatos.dia(data),
            estabelecimento: onde.isEmpty ? nil : onde,
            itens: [ItemDeGasto(
                item: item.trimmingCharacters(in: .whitespaces),
                categoria: categoria.isEmpty ? nil : categoria,
                valor: valor)])
        do {
            _ = try await sessao.cliente.lancarGastos(pedido)
            aoSalvar()
            fechar()
        } catch {
            self.erro = error.localizedDescription
        }
    }
}
