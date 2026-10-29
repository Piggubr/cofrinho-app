import SwiftUI

/// Nova receita (formulario-de-receita.ts): valor, descricao, categoria, data, se entra
/// sozinha todo mes e, com mais gente na casa, se e compartilhada.
struct NovaReceitaView: View {
    /// Mensagem e o mes em que a receita entrou.
    let aoLancar: (String, Date) -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var valor = ""
    @State private var descricao = ""
    @State private var categoria = "Salário"
    @State private var categorias: [String] = []
    /// Abre do + de qualquer aba, entao busca a familia ela mesma.
    @State private var temCompanhia = false
    @State private var data = Date()
    @State private var repetir = false
    /// So na recorrente: o salario que entra em duas vezes; o valor acima e a 1a parcela (D-087).
    @State private var emDuasParcelas = false
    @State private var segundoDia = ""
    @State private var segundoValor = ""
    /// nil segue o padrao do perfil.
    @State private var compartilhada: Bool?
    @State private var salvando = false
    @State private var erro: String?

    private var dia: Int { Calendar.current.component(.day, from: data) }
    private var compartilhadaEfetiva: Bool {
        compartilhada ?? sessao.usuario?.compartilhamento?.compartilharPorPadrao ?? false
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent("Valor") {
                        CampoDeValor(rotulo: "Valor", texto: $valor).multilineTextAlignment(.trailing)
                    }
                    TextField("Descrição", text: $descricao, prompt: Text("Salário de outubro"))
                        .onChange(of: descricao) { _, novo in if novo.count > 200 { descricao = String(novo.prefix(200)) } }
                    Picker("Categoria", selection: $categoria) {
                        ForEach(categorias.contains(categoria) ? categorias : [categoria] + categorias, id: \.self) {
                            Text($0).tag($0)
                        }
                    }
                    DatePicker("Data", selection: $data, displayedComponents: .date)
                }
                .listRowBackground(paleta.superficie)
                Section {
                    Toggle("Entra sozinha todo mês no dia \(dia) (salário, aluguel que você recebe)", isOn: $repetir)
                    if repetir {
                        Toggle("Em duas parcelas (salário com adiantamento): o valor acima é a 1ª", isOn: $emDuasParcelas)
                        if emDuasParcelas {
                            LabeledContent("Dia da 2ª parcela") {
                                TextField("Dia", text: $segundoDia)
                                    .keyboardType(.numberPad)
                                    .multilineTextAlignment(.trailing)
                                    .accessibilityLabel("Dia da 2ª parcela")
                            }
                            LabeledContent("Valor da 2ª parcela") {
                                CampoDeValor(rotulo: "Valor da 2ª parcela", texto: $segundoValor)
                                    .multilineTextAlignment(.trailing)
                            }
                        }
                    }
                    if temCompanhia {
                        Toggle("Compartilhada: aparece para quem mora com você",
                               isOn: Binding(get: { compartilhadaEfetiva }, set: { compartilhada = $0 }))
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
            .navigationTitle("Nova receita")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { fechar() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Lançar receita") { Task { await lancar() } }.disabled(salvando)
                }
            }
            .task {
                let cliente = sessao.cliente
                async let categorias = try? cliente.categoriasDeReceita()
                async let membros = try? cliente.verFamilia().membros
                self.categorias = await categorias ?? []
                temCompanhia = (await membros?.count ?? 0) > 1
            }
        }
    }

    private func lancar() async {
        let descricao = descricao.trimmingCharacters(in: .whitespaces)
        guard !descricao.isEmpty else {
            erro = String(localized: "Digite a descrição da receita.")
            return
        }
        guard let valor = Formatos.lerValor(valor), valor > 0 else {
            erro = String(localized: "Digite um valor válido.")
            return
        }
        let dividir = repetir && emDuasParcelas
        let diaDaSegunda = Int(segundoDia)
        let valorDaSegunda = Formatos.lerValor(segundoValor)
        if dividir, !(1...31).contains(diaDaSegunda ?? 0) {
            erro = String(localized: "Informe o dia da 2ª parcela (1 a 31).")
            return
        }
        if dividir, (valorDaSegunda ?? 0) <= 0 {
            erro = String(localized: "Digite o valor da 2ª parcela.")
            return
        }
        salvando = true
        defer { salvando = false }
        do {
            try await sessao.cliente.lancarReceita(NovaReceita(
                data: Formatos.dia(data), descricao: descricao, categoria: categoria, valor: valor,
                compartilhado: temCompanhia && compartilhadaEfetiva, recorrente: repetir,
                segundoDia: dividir ? diaDaSegunda : nil, segundoValor: dividir ? valorDaSegunda : nil))
            let mensagem = !repetir ? String(localized: "Receita lançada.")
                : dividir ? String(localized: "Receita lançada. Ela entra sozinha todo dia \(dia) e a 2ª parcela todo dia \(diaDaSegunda ?? 0).")
                : String(localized: "Receita lançada. Ela entra sozinha todo dia \(dia).")
            aoLancar(mensagem, data)
            fechar()
        } catch {
            self.erro = error.localizedDescription
        }
    }
}

/// Guardar o que alguem te deve: quem, do que, quanto e a data combinada.
struct NovoAReceberView: View {
    let aoGuardar: () -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var quem = ""
    @State private var motivo = ""
    @State private var valor = ""
    @State private var data = Date()
    @State private var faltou: String?
    @State private var salvando = false
    @State private var erro: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Quem deve", text: $quem, prompt: Text("Ex.: João"))
                        .listRowBackground(faltou == "quem" ? paleta.perigoSuave : paleta.superficie)
                        .onChange(of: quem) { _, novo in if novo.count > 80 { quem = String(novo.prefix(80)) } }
                    TextField("Do quê (opcional)", text: $motivo, prompt: Text("Ex.: Metade da pizza"))
                        .onChange(of: motivo) { _, novo in if novo.count > 200 { motivo = String(novo.prefix(200)) } }
                    LabeledContent("Valor") {
                        CampoDeValor(rotulo: "Valor", texto: $valor).multilineTextAlignment(.trailing)
                    }
                    .listRowBackground(faltou == "valor" ? paleta.perigoSuave : paleta.superficie)
                    DatePicker("Combinado para", selection: $data, displayedComponents: .date)
                }
                .listRowBackground(paleta.superficie)
                if let erro {
                    Section { Text(erro).foregroundStyle(paleta.perigo) }
                        .listRowBackground(paleta.perigoSuave)
                }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .navigationTitle("A receber")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { fechar() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Guardar a receber") { Task { await guardar() } }.disabled(salvando)
                }
            }
        }
    }

    private func guardar() async {
        let quem = quem.trimmingCharacters(in: .whitespaces)
        faltou = nil
        guard !quem.isEmpty else {
            faltou = "quem"
            erro = String(localized: "Diga quem está te devendo.")
            return
        }
        guard let valor = Formatos.lerValor(valor), valor > 0 else {
            faltou = "valor"
            erro = String(localized: "Digite quanto vão te pagar.")
            return
        }
        salvando = true
        defer { salvando = false }
        do {
            try await sessao.cliente.criarAReceber(NovoAReceber(
                quem: quem, descricao: motivo.trimmingCharacters(in: .whitespaces), valor: valor, data: Formatos.dia(data)))
            aoGuardar()
            fechar()
        } catch {
            self.erro = error.localizedDescription
        }
    }
}
