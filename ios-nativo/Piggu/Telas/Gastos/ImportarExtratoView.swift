import SwiftUI
import UniformTypeIdentifiers

/// Importar extrato OFX/CSV com previa (importar-extrato.ts): o que ja entrou vem desmarcado,
/// cada linha pode trocar de categoria e todas podem ir para uma conta. Nada grava antes do
/// "Importar".
struct ImportarExtratoView: View {
    /// Gravou: as outras telas recarregam.
    let aoImportar: () -> Void

    /// Arquivo ate 2 MB: o mesmo limite do servidor.
    static let tamanhoMaximo = 2_000_000
    /// OFX nao tem tipo do sistema; o da extensao casa com os arquivos .ofx do app Arquivos.
    static let tipos: [UTType] = [.commaSeparatedText, .plainText, UTType(filenameExtension: "ofx") ?? .data]

    @Environment(Sessao.self) private var sessao
    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var escolhendo = false
    @State private var ocupado = false
    @State private var linhas: [LinhaDoExtrato] = []
    @State private var marcadas: Set<String> = []
    @State private var categorias: [String] = []
    @State private var contas: [ContaOuCartao] = []
    @State private var contaId: String?
    @State private var erro: String?
    @State private var resultado: String?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("Importe o extrato do banco em OFX ou CSV (colunas data, descrição e valor). Você confere antes de gravar, e o que já foi importado fica de fora.")
                        .font(.sora(.subheadline))
                    Button { escolhendo = true } label: {
                        Label("Escolher extrato", systemImage: "doc.badge.plus")
                    }
                    .disabled(ocupado)
                }
                .listRowBackground(paleta.superficie)
                if let erro {
                    Faixa(texto: erro, tipo: .erro).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
                }
                if let resultado {
                    Faixa(texto: resultado, tipo: .sucesso).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
                }
                if ocupado && linhas.isEmpty {
                    Section { EsqueletoDeLista(linhas: 4) }.listRowBackground(paleta.superficie)
                }
                if !linhas.isEmpty { previa }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .navigationTitle("Importar extrato")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Fechar") { fechar() } }
                if !linhas.isEmpty {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Importar") { Task { await importar() } }
                            .disabled(ocupado || marcadas.isEmpty)
                    }
                }
            }
            .fileImporter(isPresented: $escolhendo, allowedContentTypes: Self.tipos) { escolha in
                Task { await ler(escolha) }
            }
            .task {
                async let categorias = try? sessao.cliente.listarCategorias()
                async let contas = try? sessao.cliente.contas()
                self.categorias = await categorias ?? []
                self.contas = await contas ?? []
            }
        }
    }

    private var previa: some View {
        let total = linhas.filter { marcadas.contains($0.id) }.reduce(Decimal(0)) { $0 + $1.valor }
        return Section {
            if !contas.isEmpty {
                Picker("Conta do extrato", selection: $contaId) {
                    Text("Não informar").tag(String?.none)
                    ForEach(contas) { Text($0.nome).tag(String?.some($0.id)) }
                }
            }
            ForEach($linhas) { $linha in
                linhaDaPrevia($linha)
            }
        } header: {
            Text("\(marcadas.count) de \(linhas.count) marcados · \(Formatos.moeda(total))")
        }
        .listRowBackground(paleta.superficie)
    }

    private func linhaDaPrevia(_ linha: Binding<LinhaDoExtrato>) -> some View {
        let atual = linha.wrappedValue
        let marcada = marcadas.contains(atual.id)
        let detalhe = atual.jaImportada
            ? String(localized: "\(Formatos.dataCurta(atual.data)) · já importado")
            : Formatos.dataCurta(atual.data)
        return VStack(alignment: .leading, spacing: 4) {
            Button {
                if marcada { marcadas.remove(atual.id) } else { marcadas.insert(atual.id) }
            } label: {
                LadoALado {
                    Image(systemName: marcada ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(marcada ? paleta.primaria : paleta.textoSuave)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(atual.descricao)
                        Text(detalhe).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    }
                    Spacer()
                    Text(Formatos.moeda(atual.valor)).monospacedDigit()
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(marcada ? .isSelected : [])
            .accessibilityHint("Toque para marcar ou desmarcar")
            // A categoria sugerida pode nao estar na lista da conta (veio de uma regra antiga).
            Picker("Categoria de \(atual.descricao)", selection: linha.categoria) {
                ForEach(categorias.contains(atual.categoria) ? categorias : [atual.categoria] + categorias,
                        id: \.self) { Text($0).tag($0) }
            }
            .labelsHidden()
            .font(.sora(.footnote))
        }
        .opacity(atual.jaImportada ? 0.6 : 1)
    }

    private func ler(_ escolha: Result<URL, Error>) async {
        erro = nil
        resultado = nil
        let dados: Data
        do {
            let url = try escolha.get()
            let liberado = url.startAccessingSecurityScopedResource()
            defer { if liberado { url.stopAccessingSecurityScopedResource() } }
            dados = try Data(contentsOf: url)
        } catch {
            erro = error.localizedDescription
            return
        }
        guard dados.count <= Self.tamanhoMaximo else {
            erro = String(localized: "Arquivo grande demais (máximo 2 MB).")
            return
        }
        ocupado = true
        defer { ocupado = false }
        do {
            let novas = try await sessao.cliente.previaDoExtrato(Self.texto(de: dados))
            linhas = novas
            marcadas = Set(novas.filter { !$0.jaImportada }.map(\.id))
            if novas.isEmpty { erro = String(localized: "Nenhum gasto encontrado no extrato.") }
        } catch {
            erro = error.localizedDescription
        }
    }

    private func importar() async {
        ocupado = true
        defer { ocupado = false }
        do {
            let feito = try await sessao.cliente.importarExtrato(linhas.filter { marcadas.contains($0.id) },
                                                                 contaId: contaId)
            resultado = feito.pulados > 0
                ? String(localized: "Importados: \(feito.importados). Já estavam no Piggu: \(feito.pulados).")
                : String(localized: "Importados: \(feito.importados).")
            erro = nil
            linhas = []
            marcadas = []
            aoImportar()
        } catch {
            erro = error.localizedDescription
        }
    }

    /// OFX de banco brasileiro costuma vir em Latin-1: se nao for UTF-8 valido, le assim.
    static func texto(de dados: Data) -> String {
        // Latin-1 aceita qualquer byte: o "" nunca acontece.
        String(data: dados, encoding: .utf8) ?? String(data: dados, encoding: .isoLatin1) ?? ""
    }
}
