import SwiftUI
import UIKit

/// Girls Math (features/girls-math): o que a pessoa viu, quis e nao comprou, e quanto isso deixou
/// no bolso. Produto, loja, valor e uma foto opcional; so de quem lancou. Nao vira gasto.
struct GirlsMathView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var carga: Carga<[ItemDoGirlsMath]> = .carregando
    @State private var produto = ""
    @State private var loja = ""
    @State private var valor = ""
    @State private var foto: UIImage?
    @State private var salvando = false
    @State private var erro: String?
    @State private var apagando: ItemDoGirlsMath?

    var body: some View {
        List {
            if let erro {
                Faixa(texto: erro, tipo: .erro).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            }
            placar
            formulario
            switch carga {
            case .carregando:
                Section { EsqueletoDeLista(linhas: 3) }.listRowBackground(paleta.superficie)
            case let .falhou(erro):
                EstadoDeErro(erro: erro) { Task { await carregar() } }.listRowBackground(Color.clear)
            case let .pronto(itens, guardadoEm):
                if let guardadoEm { AvisoDeGuardado(guardadoEm: guardadoEm) }
                if itens.isEmpty {
                    EstadoVazio(icone: "bag", titulo: "Nenhum item guardado ainda",
                                texto: "Registre o que você viu, quis e não comprou, e veja quanto ficou no bolso.")
                        .listRowBackground(Color.clear)
                } else {
                    Section { ForEach(itens) { linha($0) } }.listRowBackground(paleta.superficie)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(paleta.fundo)
        .navigationTitle("Girls Math")
        .refreshable { await carregar() }
        .task(id: rede.conectado) { await carregar() }
        .confirmationDialog(apagando.map { String(localized: "Apagar \"\($0.produto)\"?") } ?? "",
                            isPresented: Binding(get: { apagando != nil }, set: { if !$0 { apagando = nil } }),
                            titleVisibility: .visible, presenting: apagando) { item in
            Button("Apagar", role: .destructive) { Task { await excluir(item) } }
        }
    }

    private var placar: some View {
        let itens = carga.valor ?? []
        let mes = Formatos.mes(Date())
        return Section {
            VStack(spacing: 4) {
                Text("Você deixou de gastar este mês").font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
                Text(Formatos.moeda(itens.filter { $0.data.hasPrefix(mes) }.reduce(0) { $0 + $1.valor }))
                    .font(.sora(.largeTitle, peso: .bold)).foregroundStyle(paleta.primaria).monospacedDigit()
                    .contentTransition(.numericText())
                Text("Desde o começo: \(Formatos.moeda(itens.reduce(0) { $0 + $1.valor }))")
                    .font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .accessibilityElement(children: .combine)
        } header: {
            Text("Registre o que você deixou de comprar.")
        }
        .listRowBackground(paleta.superficie)
    }

    private var formulario: some View {
        Section("O que você não comprou?") {
            HStack(spacing: 12) {
                Group {
                    if let foto {
                        Image(uiImage: foto).resizable().scaledToFill()
                    } else {
                        Image(systemName: "camera").font(.title3).foregroundStyle(paleta.primaria)
                    }
                }
                .frame(width: 64, height: 64)
                .background(paleta.suave)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .accessibilityLabel(foto == nil ? "Sem foto" : "Foto do produto")
                VStack(alignment: .leading) {
                    BotoesDeFoto(tituloDaGaleria: "Foto") { foto = $0 }
                    if foto != nil {
                        Button("Tirar a foto", role: .destructive) { foto = nil }.font(.sora(.footnote))
                    }
                }
            }
            TextField("Nome do produto", text: $produto)
                .onChange(of: produto) { _, novo in if novo.count > 120 { produto = String(novo.prefix(120)) } }
            TextField("Loja onde você viu", text: $loja)
                .onChange(of: loja) { _, novo in if novo.count > 120 { loja = String(novo.prefix(120)) } }
            LabeledContent("Quanto custava (\(Formatos.simboloDaMoeda))") {
                CampoDeValor(rotulo: "Quanto custava", texto: $valor).multilineTextAlignment(.trailing)
            }
            Button(salvando ? "Guardando..." : "Guardar no Girls Math") { Task { await guardar() } }
                .disabled(salvando)
        }
        .listRowBackground(paleta.superficie)
    }

    private func linha(_ item: ItemDoGirlsMath) -> some View {
        HStack(spacing: 12) {
            Group {
                if item.temFoto == true, let id = item.fotoAssetId {
                    FotoDoAsset(assetId: id)
                } else {
                    Image(systemName: "bag").foregroundStyle(paleta.primaria).frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(paleta.suave)
                }
            }
            .frame(width: 44, height: 44)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            LinhaDoCartao(titulo: item.produto,
                          detalhe: [Formatos.dataCurta(item.data), item.loja ?? ""].filter { !$0.isEmpty }.joined(separator: " · ")) {
                Text(Formatos.moeda(item.valor)).monospacedDigit()
            }
        }
        .swipeActions {
            Button(role: .destructive) { apagando = item } label: { Label("Apagar", systemImage: "trash") }
        }
    }

    private func carregar() async {
        await sessao.cache.carregar("girls-math", mostrar: { carga = $0 }) { try await sessao.cliente.listarGirlsMath() }
    }

    private func guardar() async {
        let produto = produto.trimmingCharacters(in: .whitespaces)
        guard !produto.isEmpty else {
            erro = String(localized: "Digite o nome do produto.")
            return
        }
        guard let valor = Formatos.lerValor(valor), valor > 0 else {
            erro = String(localized: "Digite quanto custava.")
            return
        }
        salvando = true
        erro = nil
        defer { salvando = false }
        let jpeg = foto.flatMap(Imagem.fotoDeProduto)
        let loja = loja.trimmingCharacters(in: .whitespaces)
        do {
            let item = try await sessao.cliente.criarNoGirlsMath(NovoItemDoGirlsMath(
                produto: produto, loja: loja.isEmpty ? nil : loja, valor: valor, data: Formatos.dia(Date()),
                imageBase64: jpeg?.base64EncodedString(), mimeType: jpeg == nil ? nil : "image/jpeg"))
            if case let .pronto(itens, _) = carga { carga = .pronto([item] + itens) }
            self.produto = ""
            self.loja = ""
            self.valor = ""
            foto = nil
        } catch {
            self.erro = error.localizedDescription
        }
    }

    private func excluir(_ item: ItemDoGirlsMath) async {
        do {
            try await sessao.cliente.excluirDoGirlsMath(item.id)
            if case let .pronto(itens, _) = carga { carga = .pronto(itens.filter { $0.id != item.id }) }
        } catch {
            erro = error.localizedDescription
        }
    }
}
