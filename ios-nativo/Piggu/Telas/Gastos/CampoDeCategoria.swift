import SwiftUI

/// Categoria com "+ Nova categoria…" na propria lista (campo-de-categoria.ts): escolher a
/// ultima opcao troca a lista por um campo de nome; ao criar, a categoria ja fica escolhida.
struct CampoDeCategoria: View {
    /// Vazia e "Automática": o Piggu escolhe pelas regras.
    @Binding var categoria: String
    @Binding var categorias: [String]
    /// Gasto aceita "Automática"; a edicao de um gasto, nao.
    var automatica = true

    private static let nova = "__nova__"

    @Environment(Sessao.self) private var sessao
    @Environment(\.paleta) private var paleta
    @State private var criando = false
    @State private var nome = ""
    @State private var erro: String?
    @State private var salvando = false
    @FocusState private var focado: Bool

    var body: some View {
        if criando {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    TextField("Nome da nova categoria", text: $nome, prompt: Text("Ex.: Pet"))
                        .focused($focado)
                        .submitLabel(.done)
                        .onSubmit { Task { await criar() } }
                        .onChange(of: nome) { _, novo in
                            erro = nil
                            if novo.count > 50 { nome = String(novo.prefix(50)) }
                        }
                    Button("Criar") { Task { await criar() } }
                        .buttonStyle(.borderless)
                        .disabled(salvando)
                    Button("Cancelar") { criando = false }
                        .buttonStyle(.borderless)
                        .foregroundStyle(paleta.textoSuave)
                }
                if let erro {
                    Text(erro).font(.sora(.footnote)).foregroundStyle(paleta.perigo)
                }
            }
            .onAppear { focado = true }
        } else {
            Picker("Categoria", selection: Binding(get: { categoria }, set: escolher)) {
                if automatica {
                    Text("Automática").tag("")
                }
                ForEach(categorias, id: \.self) { Text($0).tag($0) }
                Text("+ Nova categoria…").tag(Self.nova)
            }
        }
    }

    private func escolher(_ escolhida: String) {
        guard escolhida == Self.nova else {
            categoria = escolhida
            return
        }
        nome = ""
        erro = nil
        criando = true
    }

    private func criar() async {
        guard !salvando else { return }
        let nome = nome.split(whereSeparator: \.isWhitespace).joined(separator: " ")
        guard nome.count >= 2 else {
            erro = String(localized: "Digite o nome da categoria.")
            return
        }
        // Ja existe (talvez com outra caixa): so escolhe, sem erro.
        if let existente = categorias.first(where: { $0.lowercased() == nome.lowercased() }) {
            categoria = existente
            criando = false
            return
        }
        salvando = true
        defer { salvando = false }
        do {
            categorias = try await sessao.cliente.criarCategoria(nome)
            categoria = categorias.first { $0.lowercased() == nome.lowercased() } ?? nome
            criando = false
        } catch {
            erro = error.localizedDescription
        }
    }
}
