import SwiftUI

/// Filmes (features/movies): a lista do casal, buscar e sortear no TMDB, marcar como assistido e a
/// nota de cada pessoa, de 1 a 5, mostradas separadas em vez de uma media precoce.
struct FilmesView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var carga: Carga<[Filme]> = .carregando
    @State private var membros: [MembroDaFamilia] = []
    @State private var genero = ""
    @State private var sorteando = false
    @State private var sorteado: FilmeDoCatalogo?
    @State private var busca = ""
    @State private var buscando = false
    @State private var resultados: [FilmeDoCatalogo] = []
    @State private var tirando: Filme?
    @State private var erro: String?

    static let generos: [(id: String, nome: LocalizedStringKey)] = [
        ("", "Qualquer gênero"), ("28", "Ação"), ("35", "Comédia"), ("18", "Drama"), ("27", "Terror"),
        ("10749", "Romance"), ("878", "Ficção científica"), ("16", "Animação"), ("53", "Suspense"),
    ]

    private var eu: String { sessao.usuario?.id ?? "" }

    var body: some View {
        List {
            if let erro {
                Faixa(texto: erro, tipo: .erro).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            }
            Section("O que vamos assistir?") {
                Picker("Gênero", selection: $genero) {
                    ForEach(Self.generos, id: \.id) { Text($0.nome).tag($0.id) }
                }
                Button(sorteando ? "Sorteando..." : "Sortear filme") { Task { await sortear() } }.disabled(sorteando)
                if let sorteado {
                    cartaoDoCatalogo(sorteado, destaque: true)
                }
                LadoALado {
                    TextField("Buscar filme", text: $busca, prompt: Text("Nome do filme"))
                        .submitLabel(.search)
                        .onSubmit { Task { await buscar() } }
                    Button(buscando ? "Buscando..." : "Buscar") { Task { await buscar() } }
                        .buttonStyle(.borderless).disabled(buscando)
                }
                ForEach(resultados) { cartaoDoCatalogo($0, destaque: false) }
            }
            .listRowBackground(paleta.superficie)

            switch carga {
            case .carregando:
                Section { EsqueletoDeLista(linhas: 3) }.listRowBackground(paleta.superficie)
            case let .falhou(erro):
                EstadoDeErro(erro: erro) { Task { await carregar() } }.listRowBackground(Color.clear)
            case let .pronto(filmes, guardadoEm):
                if let guardadoEm { AvisoDeGuardado(guardadoEm: guardadoEm) }
                let paraVer = filmes.filter { !$0.assistido }
                let assistidos = filmes.filter(\.assistido)
                Section {
                    if paraVer.isEmpty {
                        EstadoVazio(icone: "film", titulo: "Nenhum filme na fila",
                                    texto: "Busque um filme e guarde para a próxima noite de sofá.")
                    }
                    ForEach(paraVer) { filme in
                        LinhaDoFilme(filme: filme) {
                            Button("Já assistimos") { Task { await alternar(filme) } }
                                .buttonStyle(.bordered).controlSize(.small)
                        }
                        .swipeActions { apagar(filme) }
                    }
                } header: {
                    HStack { Text("Para assistir"); Spacer(); Text("\(paraVer.count)") }
                }
                .listRowBackground(paraVer.isEmpty ? Color.clear : paleta.superficie)
                Section {
                    if assistidos.isEmpty {
                        Text("Nenhum filme assistido ainda").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    }
                    ForEach(assistidos) { filme in
                        LinhaDoFilme(filme: filme) {
                            VStack(alignment: .trailing, spacing: 4) {
                                Estrelas(nota: filme.avaliacoes[eu] ?? 0) { nota in Task { await avaliar(filme, nota) } }
                                ForEach(filme.avaliacoes.filter { $0.key != eu }.sorted { $0.key < $1.key }, id: \.key) { pessoa, nota in
                                    Text("\(PessoasDaFamilia.nome(pessoa, em: membros)): \(nota) ★")
                                        .font(.sora(.caption)).foregroundStyle(paleta.textoSuave)
                                }
                                Button("Voltar para a fila") { Task { await alternar(filme) } }
                                    .font(.sora(.caption)).buttonStyle(.borderless)
                            }
                        }
                        .swipeActions { apagar(filme) }
                    }
                } header: {
                    HStack { Text("Já assistidos"); Spacer(); Text("\(assistidos.count)") }
                }
                .listRowBackground(paleta.superficie)
            }
        }
        .scrollContentBackground(.hidden)
        .background(paleta.fundo)
        .navigationTitle("Filmes")
        .refreshable { await carregar() }
        .task(id: rede.conectado) { await carregar() }
        .confirmationDialog(tirando.map { String(localized: "Tirar \"\($0.titulo)\" da lista?") } ?? "",
                            isPresented: Binding(get: { tirando != nil }, set: { if !$0 { tirando = nil } }),
                            titleVisibility: .visible, presenting: tirando) { filme in
            Button("Tirar da lista", role: .destructive) { Task { await excluir(filme) } }
        }
    }

    private func apagar(_ filme: Filme) -> some View {
        Button(role: .destructive) { tirando = filme } label: { Label("Apagar", systemImage: "trash") }
    }

    private func cartaoDoCatalogo(_ filme: FilmeDoCatalogo, destaque: Bool) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Miniatura(url: filme.poster, simbolo: "film", lado: destaque ? 80 : 52)
            VStack(alignment: .leading, spacing: 4) {
                Text("\(filme.titulo) \(Text(filme.ano ?? "").foregroundStyle(paleta.textoSuave))")
                    .font(.sora(.subheadline, peso: .semibold))
                if let nota = filme.nota {
                    Text("Nota TMDB: \(nota.formatted(.number.precision(.fractionLength(1)).locale(Formatos.localidade)))")
                        .font(.sora(.caption)).foregroundStyle(paleta.textoSuave)
                }
                if destaque, let sinopse = filme.sinopse, !sinopse.isEmpty {
                    Text(sinopse).font(.sora(.footnote)).lineLimit(4)
                }
                Button(destaque ? "Adicionar à lista" : "Adicionar") { Task { await adicionar(filme) } }
                    .buttonStyle(.bordered).controlSize(.small)
            }
        }
        .padding(.vertical, 2)
    }

    // MARK: - API

    private func carregar() async {
        async let membros = try? sessao.cliente.verFamilia().membros
        await sessao.cache.carregar("filmes", mostrar: { carga = $0 }) { try await sessao.cliente.listarFilmes() }
        self.membros = await membros ?? self.membros
    }

    private func sortear() async {
        sorteando = true
        defer { sorteando = false }
        do {
            sorteado = try await sessao.cliente.sortearFilme(genero: genero.isEmpty ? nil : genero)
            erro = nil
        } catch {
            erro = error.localizedDescription
        }
    }

    private func buscar() async {
        let termo = busca.trimmingCharacters(in: .whitespaces)
        guard termo.count >= 2 else {
            erro = String(localized: "Digite pelo menos duas letras.")
            return
        }
        buscando = true
        defer { buscando = false }
        do {
            resultados = try await sessao.cliente.buscarFilmes(termo)
            erro = nil
        } catch {
            erro = error.localizedDescription
        }
    }

    private func adicionar(_ filme: FilmeDoCatalogo) async {
        do {
            let criado = try await sessao.cliente.adicionarFilme(filme)
            if case let .pronto(filmes, _) = carga { carga = .pronto([criado] + filmes) }
            resultados = []
            sorteado = nil
            busca = ""
            erro = nil
        } catch {
            erro = error.localizedDescription
        }
    }

    private func alternar(_ filme: Filme) async {
        await substituir { try await $0.marcarFilmeAssistido(filme.id, !filme.assistido) }
    }

    private func avaliar(_ filme: Filme, _ nota: Int) async {
        await substituir { try await $0.avaliarFilme(filme.id, nota: nota) }
    }

    private func substituir(_ acao: (ClienteDaAPI) async throws -> Filme) async {
        do {
            let atualizado = try await acao(sessao.cliente)
            if case let .pronto(filmes, _) = carga { carga = .pronto(filmes.map { $0.id == atualizado.id ? atualizado : $0 }) }
            erro = nil
        } catch {
            erro = error.localizedDescription
        }
    }

    private func excluir(_ filme: Filme) async {
        do {
            try await sessao.cliente.excluirFilme(filme.id)
            if case let .pronto(filmes, _) = carga { carga = .pronto(filmes.filter { $0.id != filme.id }) }
        } catch {
            erro = error.localizedDescription
        }
    }
}

private struct LinhaDoFilme<Acoes: View>: View {
    let filme: Filme
    @ViewBuilder var acoes: Acoes

    @Environment(\.paleta) private var paleta

    var body: some View {
        LadoALado(alinhamento: .top, espaco: 12) {
            Miniatura(url: filme.poster, simbolo: "film", lado: 52)
            VStack(alignment: .leading, spacing: 2) {
                Text(filme.titulo).font(.sora(.subheadline, peso: .semibold))
                if let ano = filme.ano { Text(ano).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave) }
            }
            Spacer()
            acoes
        }
        .padding(.vertical, 2)
    }
}

/// Cinco estrelas tocaveis (a nota de lugares e filmes).
struct Estrelas: View {
    let nota: Int
    let aoEscolher: (Int) -> Void

    @Environment(\.paleta) private var paleta

    var body: some View {
        HStack(spacing: 0) {
            ForEach(1...5, id: \.self) { valor in
                Button { aoEscolher(valor) } label: {
                    Image(systemName: nota >= valor ? "star.fill" : "star")
                        .foregroundStyle(nota >= valor ? paleta.alerta : paleta.textoSuave)
                        .frame(minWidth: 34, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.borderless)
            }
        }
        // Um elemento so para o VoiceOver: desliza para cima ou para baixo para mudar a nota.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Nota")
        .accessibilityValue(nota == 0 ? String(localized: "sem nota") : String(localized: "\(nota) de 5"))
        .accessibilityAdjustableAction { direcao in
            switch direcao {
            case .increment: if nota < 5 { aoEscolher(nota + 1) }
            case .decrement: if nota > 1 { aoEscolher(nota - 1) }
            @unknown default: break
            }
        }
    }
}
