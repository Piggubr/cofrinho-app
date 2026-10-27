import SwiftUI

/// Compras (features/shopping): as listas de compras e de desejos. A busca no catalogo aberto
/// preenche marca, imagem e codigo de barras sem digitar.
struct ComprasView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var carga: Carga<[ItemDeCompra]> = .carregando
    @State private var lista = ItemDeCompra.Lista.compras
    @State private var item = ""
    @State private var quantidade = ""
    @State private var busca = ""
    @State private var buscando = false
    @State private var resultados: [ProdutoDoCatalogo] = []
    @State private var erro: String?

    var body: some View {
        List {
            if let erro {
                Faixa(texto: erro, tipo: .erro).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            }
            Section {
                Picker("Lista", selection: $lista) {
                    Text("Compras").tag(ItemDeCompra.Lista.compras)
                    Text("Desejos").tag(ItemDeCompra.Lista.desejos)
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                HStack {
                    TextField("Item", text: $item, prompt: Text("Ex.: Ovos"))
                        .onChange(of: item) { _, novo in if novo.count > 150 { item = String(novo.prefix(150)) } }
                    TextField("Quantidade", text: $quantidade, prompt: Text("Ex.: 12"))
                        .frame(maxWidth: 110)
                        .onChange(of: quantidade) { _, novo in if novo.count > 50 { quantidade = String(novo.prefix(50)) } }
                }
                Button("Adicionar") { Task { await adicionar() } }
                HStack {
                    TextField("Buscar no catálogo", text: $busca, prompt: Text("Nome do produto"))
                        .submitLabel(.search)
                        .onSubmit { Task { await buscar() } }
                    Button(buscando ? "Buscando..." : "Buscar") { Task { await buscar() } }
                        .buttonStyle(.borderless).disabled(buscando)
                }
                ForEach(resultados) { produto in
                    Button { Task { await adicionar(produto) } } label: {
                        HStack(spacing: 12) {
                            Miniatura(url: produto.imagem, simbolo: "cart")
                            VStack(alignment: .leading, spacing: 2) {
                                Text(produto.nome).font(.sora(.subheadline, peso: .semibold))
                                Text([produto.marca, produto.quantidade].compactMap { $0 }.joined(separator: " "))
                                    .font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                            }
                            Spacer()
                            Image(systemName: "plus.circle.fill").foregroundStyle(paleta.primaria)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint("Adiciona à lista")
                }
            }
            .listRowBackground(paleta.superficie)

            switch carga {
            case .carregando:
                Section { EsqueletoDeLista(linhas: 4) }.listRowBackground(paleta.superficie)
            case let .falhou(erro):
                EstadoDeErro(erro: erro) { Task { await carregar() } }.listRowBackground(Color.clear)
            case let .pronto(itens, guardadoEm):
                if let guardadoEm { AvisoDeGuardado(guardadoEm: guardadoEm) }
                let daLista = itens.filter { $0.lista == lista }
                let pendentes = daLista.filter { !$0.comprado }
                let comprados = daLista.filter(\.comprado)
                Section {
                    if pendentes.isEmpty {
                        EstadoVazio(icone: "cart", titulo: "Lista vazia",
                                    texto: "Anote o que falta em casa e marque conforme for comprando.")
                    }
                    ForEach(pendentes) { linha($0) }
                } header: {
                    HStack { Text("A comprar"); Spacer(); Text("\(pendentes.count)") }
                }
                .listRowBackground(pendentes.isEmpty ? Color.clear : paleta.superficie)
                if !comprados.isEmpty {
                    Section {
                        ForEach(comprados) { linha($0) }
                    } header: {
                        HStack { Text("Já no carrinho"); Spacer(); Text("\(comprados.count)") }
                    }
                    .listRowBackground(paleta.superficie)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(paleta.fundo)
        .navigationTitle("Compras")
        .refreshable { await carregar() }
        .task(id: rede.conectado) { await carregar() }
    }

    private func linha(_ item: ItemDeCompra) -> some View {
        Button { Task { await alternar(item) } } label: {
            HStack(spacing: 12) {
                Image(systemName: item.comprado ? "checkmark.circle.fill" : "circle")
                    .font(.title3).foregroundStyle(item.comprado ? paleta.sucesso : paleta.textoSuave)
                if let imagem = item.imagem, !imagem.isEmpty, !item.comprado {
                    Miniatura(url: imagem, simbolo: "cart")
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.item).strikethrough(item.comprado)
                        .foregroundStyle(item.comprado ? paleta.textoSuave : paleta.texto)
                    let detalhe = [item.marca, item.quantidade].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " ")
                    if !detalhe.isEmpty && !item.comprado {
                        Text(detalhe).font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                    }
                }
                Spacer()
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(item.comprado ? .isSelected : [])
        .swipeActions {
            Button(role: .destructive) { Task { await excluir(item) } } label: { Label("Apagar", systemImage: "trash") }
        }
    }

    // MARK: - API

    private func carregar() async {
        await sessao.cache.carregar("compras", mostrar: { carga = $0 }) { try await sessao.cliente.listarCompras() }
    }

    private func adicionar() async {
        let item = item.trimmingCharacters(in: .whitespaces)
        guard !item.isEmpty else {
            erro = String(localized: "Digite o que deseja adicionar.")
            return
        }
        await inserir(NovoItemDeCompra(item: item, quantidade: quantidade.trimmingCharacters(in: .whitespaces), lista: lista))
        if erro == nil {
            self.item = ""
            quantidade = ""
        }
    }

    private func adicionar(_ produto: ProdutoDoCatalogo) async {
        await inserir(NovoItemDeCompra(item: produto.nome, quantidade: produto.quantidade ?? "", lista: lista,
                                       marca: produto.marca, imagem: produto.imagem, codigo: produto.codigo))
        if erro == nil {
            resultados = []
            busca = ""
        }
    }

    private func inserir(_ novo: NovoItemDeCompra) async {
        do {
            let criado = try await sessao.cliente.criarItemDeCompra(novo)
            if case let .pronto(itens, _) = carga { carga = .pronto([criado] + itens) }
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
            resultados = try await sessao.cliente.buscarNoCatalogo(termo)
            erro = nil
        } catch {
            erro = error.localizedDescription
        }
    }

    /// Marca na hora e desfaz se o servidor recusar: esperar a resposta deixaria a lista lenta no mercado.
    private func alternar(_ item: ItemDeCompra) async {
        aplicar(item.id, !item.comprado)
        do {
            try await sessao.cliente.marcarComprado(item.id, !item.comprado)
        } catch {
            aplicar(item.id, item.comprado)
            erro = error.localizedDescription
        }
    }

    private func aplicar(_ id: String, _ comprado: Bool) {
        guard case let .pronto(itens, guardadoEm) = carga else { return }
        carga = .pronto(itens.map { atual in
            var copia = atual
            if atual.id == id { copia.comprado = comprado }
            return copia
        }, guardadoEm: guardadoEm)
    }

    private func excluir(_ item: ItemDeCompra) async {
        do {
            try await sessao.cliente.excluirItemDeCompra(item.id)
            if case let .pronto(itens, _) = carga { carga = .pronto(itens.filter { $0.id != item.id }) }
        } catch {
            erro = error.localizedDescription
        }
    }
}

/// Imagem pequena de um endereco HTTPS publico (catalogo, poster), com um simbolo enquanto nao chega.
struct Miniatura: View {
    let url: String?
    let simbolo: String
    var lado: CGFloat = 40

    @Environment(\.paleta) private var paleta

    var body: some View {
        Group {
            if let url, let endereco = URL(string: url), endereco.scheme == "https" {
                AsyncImage(url: endereco) { $0.resizable().scaledToFill() } placeholder: { vazio }
            } else {
                vazio
            }
        }
        .frame(width: lado, height: lado)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        .accessibilityHidden(true)
    }

    private var vazio: some View {
        Image(systemName: simbolo).foregroundStyle(paleta.primaria)
            .frame(maxWidth: .infinity, maxHeight: .infinity).background(paleta.suave)
    }
}
