import MapKit
import SwiftUI
import UIKit

/// Lugares (features/places): os restaurantes e passeios do casal, com nota, comentario, quanto
/// gastaram, marcadores e foto. No app, os lugares tambem aparecem num mapa (MapKit): a
/// localizacao escrita vira um ponto pela busca do Mapas.
struct LugaresView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var carga: Carga<[Lugar]> = .carregando
    @State private var marcadores: [String] = []
    @State private var pontos: [String: MKMapItem] = [:]
    @State private var escolhido: String?
    @State private var formulario: FormularioDeLugar?
    @State private var apagando: Lugar?
    @State private var erro: String?

    var body: some View {
        List {
            if let erro {
                Faixa(texto: erro, tipo: .erro).listRowBackground(Color.clear).listRowInsets(EdgeInsets())
            }
            switch carga {
            case .carregando:
                Section { EsqueletoDeLista(linhas: 3) }.listRowBackground(paleta.superficie)
            case let .falhou(erro):
                EstadoDeErro(erro: erro) { Task { await carregar() } }.listRowBackground(Color.clear)
            case let .pronto(lugares, guardadoEm):
                if let guardadoEm { AvisoDeGuardado(guardadoEm: guardadoEm) }
                if lugares.isEmpty {
                    EstadoVazio(icone: "mappin.and.ellipse", titulo: "Nenhum lugar registrado ainda",
                                texto: "Guarde os restaurantes e passeios que vocês querem lembrar.",
                                acao: ("Registrar um lugar", { formulario = FormularioDeLugar(lugar: nil) }))
                        .listRowBackground(Color.clear)
                } else {
                    if !pontos.isEmpty {
                        Section { mapa(lugares) }
                            .listRowInsets(EdgeInsets())
                            .listRowBackground(Color.clear)
                    }
                    Section { ForEach(lugares) { cartao($0) } }.listRowBackground(paleta.superficie)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(paleta.fundo)
        .navigationTitle("Lugares")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { formulario = FormularioDeLugar(lugar: nil) } label: { Label("Novo lugar", systemImage: "plus") }
            }
        }
        .refreshable { await carregar() }
        .task(id: rede.conectado) { await carregar() }
        .sheet(item: $formulario) { formulario in
            LugarView(lugar: formulario.lugar, marcadores: $marcadores) { salvo in
                if case let .pronto(lugares, _) = carga {
                    carga = .pronto([salvo] + lugares.filter { $0.id != salvo.id })
                }
                Task { await localizar([salvo]) }
            }
        }
        .confirmationDialog(apagando.map { String(localized: "Apagar \"\($0.nome)\"?") } ?? "",
                            isPresented: Binding(get: { apagando != nil }, set: { if !$0 { apagando = nil } }),
                            titleVisibility: .visible, presenting: apagando) { lugar in
            Button("Apagar", role: .destructive) { Task { await excluir(lugar) } }
        }
    }

    private func mapa(_ lugares: [Lugar]) -> some View {
        Map(selection: $escolhido) {
            ForEach(lugares.filter { pontos[$0.id] != nil }) { lugar in
                Marker(lugar.nome, systemImage: "fork.knife", coordinate: pontos[lugar.id]!.placemark.coordinate)
                    .tint(paleta.primaria)
                    .tag(lugar.id)
            }
        }
        .frame(height: 220)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityLabel("Mapa com os lugares")
    }

    private func cartao(_ lugar: Lugar) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            if lugar.temFoto == true, let id = lugar.fotoAssetId {
                FotoDoAsset(assetId: id).frame(height: 160).frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            Text(lugar.nome).font(.sora(.headline, peso: .semibold))
            Text([lugar.categoria, lugar.localizacao, Formatos.dataCurta(lugar.data)].compactMap { $0 }
                .filter { !$0.isEmpty }.joined(separator: " · "))
                .font(.sora(.footnote)).foregroundStyle(paleta.textoSuave)
            Text(String(repeating: "★", count: lugar.nota)).foregroundStyle(paleta.alerta)
                .accessibilityLabel("Nota \(lugar.nota) de 5")
            if let comentario = lugar.comentario, !comentario.isEmpty {
                Text(comentario).font(.sora(.subheadline))
            }
            if lugar.valor > 0 {
                Text(Formatos.moeda(lugar.valor)).font(.sora(.subheadline, peso: .semibold)).monospacedDigit()
            }
            if !lugar.marcacoes.isEmpty {
                HStack { ForEach(lugar.marcacoes, id: \.self) { Etiqueta(texto: $0) } }
            }
            if let ponto = pontos[lugar.id] {
                Button { ponto.openInMaps() } label: { Label("Abrir no Mapas", systemImage: "map") }
                    .font(.sora(.footnote)).buttonStyle(.borderless)
            }
        }
        .padding(.vertical, 4)
        .listRowBackground(escolhido == lugar.id ? paleta.suave : paleta.superficie)
        .swipeActions {
            Button(role: .destructive) { apagando = lugar } label: { Label("Apagar", systemImage: "trash") }
            Button { formulario = FormularioDeLugar(lugar: lugar) } label: { Label("Editar", systemImage: "pencil") }
                .tint(paleta.primaria)
        }
    }

    // MARK: - API

    private func carregar() async {
        let cliente = sessao.cliente
        async let marcadores = try? cliente.listarMarcadores()
        await sessao.cache.carregar("lugares", mostrar: { carga = $0 }) { try await cliente.listarLugares() }
        self.marcadores = await marcadores ?? self.marcadores
        if let lugares = carga.valor { await localizar(lugares) }
    }

    /// A localizacao escrita ("Florianopolis, SC") vira um ponto pela busca do Mapas; sem achar, fica fora do mapa.
    private func localizar(_ lugares: [Lugar]) async {
        for lugar in lugares where pontos[lugar.id] == nil {
            guard let onde = lugar.localizacao, !onde.isEmpty else { continue }
            let busca = MKLocalSearch.Request()
            busca.naturalLanguageQuery = "\(lugar.nome), \(onde)"
            let achado = try? await MKLocalSearch(request: busca).start().mapItems.first
            if let achado { pontos[lugar.id] = achado }
        }
    }

    private func excluir(_ lugar: Lugar) async {
        do {
            try await sessao.cliente.excluirLugar(lugar.id)
            if case let .pronto(lugares, _) = carga { carga = .pronto(lugares.filter { $0.id != lugar.id }) }
        } catch {
            erro = error.localizedDescription
        }
    }
}

struct FormularioDeLugar: Identifiable {
    let id = UUID()
    let lugar: Lugar?
}

/// Novo lugar ou editar: nome, categoria, localizacao, data, quanto gastaram, nota, comentario,
/// marcadores e foto.
private struct LugarView: View {
    let lugar: Lugar?
    @Binding var marcadores: [String]
    let aoSalvar: (Lugar) -> Void

    @Environment(Sessao.self) private var sessao
    @Environment(\.dismiss) private var fechar
    @Environment(\.paleta) private var paleta
    @State private var nome = ""
    @State private var categoria = ""
    @State private var localizacao = ""
    @State private var data = Date()
    @State private var valor = ""
    @State private var nota = 5
    @State private var comentario = ""
    @State private var marcacoes: [String] = []
    @State private var novoMarcador = ""
    @State private var foto: UIImage?
    @State private var salvando = false
    @State private var erro: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Nome", text: $nome).onChange(of: nome) { _, n in if n.count > 120 { nome = String(n.prefix(120)) } }
                    TextField("Categoria", text: $categoria, prompt: Text("Ex.: Restaurante"))
                        .onChange(of: categoria) { _, n in if n.count > 50 { categoria = String(n.prefix(50)) } }
                    TextField("Localização", text: $localizacao, prompt: Text("Ex.: São Paulo"))
                        .onChange(of: localizacao) { _, n in if n.count > 200 { localizacao = String(n.prefix(200)) } }
                    DatePicker("Data da visita", selection: $data, displayedComponents: .date)
                    LabeledContent("Quanto gastamos (\(Formatos.simboloDaMoeda))") {
                        CampoDeValor(rotulo: "Quanto gastamos", texto: $valor).multilineTextAlignment(.trailing)
                    }
                    LabeledContent("Nota") { Estrelas(nota: nota) { nota = $0 } }
                    TextField("Comentário", text: $comentario, axis: .vertical).lineLimit(2...5)
                        .onChange(of: comentario) { _, n in if n.count > 500 { comentario = String(n.prefix(500)) } }
                }
                .listRowBackground(paleta.superficie)
                Section("Marcadores") {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(marcadores, id: \.self) { marcador in
                                let ativo = marcacoes.contains(marcador)
                                Button(marcador) {
                                    if ativo { marcacoes.removeAll { $0 == marcador } } else { marcacoes.append(marcador) }
                                }
                                .buttonStyle(.bordered).tint(ativo ? paleta.primaria : paleta.textoSuave)
                                .accessibilityAddTraits(ativo ? .isSelected : [])
                            }
                        }
                    }
                    HStack {
                        TextField("Criar marcador", text: $novoMarcador)
                            .onChange(of: novoMarcador) { _, n in if n.count > 40 { novoMarcador = String(n.prefix(40)) } }
                        Button("Adicionar marcador") { Task { await criarMarcador() } }.buttonStyle(.borderless)
                    }
                }
                .listRowBackground(paleta.superficie)
                Section("Foto") {
                    if let foto {
                        Image(uiImage: foto).resizable().scaledToFill().frame(height: 160).clipped()
                            .accessibilityLabel("Foto do lugar")
                    }
                    BotoesDeFoto(tituloDaGaleria: "Escolher foto") { foto = $0 }
                }
                .listRowBackground(paleta.superficie)
                if let erro {
                    Section { Text(erro).foregroundStyle(paleta.perigo) }.listRowBackground(paleta.perigoSuave)
                }
            }
            .scrollContentBackground(.hidden)
            .background(paleta.fundo)
            .navigationTitle(lugar == nil ? "Novo lugar" : "Editar lugar")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { fechar() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(salvando ? "Guardando..." : "Salvar") { Task { await salvar() } }.disabled(salvando)
                }
            }
            .onAppear(perform: preencher)
        }
    }

    private func preencher() {
        guard let lugar else { return }
        nome = lugar.nome
        categoria = lugar.categoria ?? ""
        localizacao = lugar.localizacao ?? ""
        data = Formatos.data(deDia: lugar.data) ?? Date()
        valor = lugar.valor > 0 ? Formatos.valorParaDigitar(lugar.valor) : ""
        nota = lugar.nota
        comentario = lugar.comentario ?? ""
        marcacoes = lugar.marcacoes
    }

    private func criarMarcador() async {
        let nome = novoMarcador.trimmingCharacters(in: .whitespaces)
        guard nome.count >= 2 else {
            erro = String(localized: "Digite um nome válido.")
            return
        }
        do {
            marcadores = try await sessao.cliente.criarMarcador(nome)
            novoMarcador = ""
            erro = nil
        } catch {
            self.erro = error.localizedDescription
        }
    }

    private func salvar() async {
        let nome = nome.trimmingCharacters(in: .whitespaces)
        guard !nome.isEmpty else {
            erro = String(localized: "Digite o nome do lugar.")
            return
        }
        func ouNil(_ texto: String) -> String? {
            let limpo = texto.trimmingCharacters(in: .whitespaces)
            return limpo.isEmpty ? nil : limpo
        }
        let jpeg = foto.flatMap(Imagem.fotoDeProduto)
        salvando = true
        defer { salvando = false }
        do {
            let salvo = try await sessao.cliente.salvarLugar(NovoLugar(
                nome: nome, categoria: ouNil(categoria), localizacao: ouNil(localizacao), nota: nota,
                comentario: ouNil(comentario), data: Formatos.dia(data), marcacoes: marcacoes,
                valor: Formatos.lerValor(valor) ?? 0, imageBase64: jpeg?.base64EncodedString(),
                mimeType: jpeg == nil ? nil : "image/jpeg"), id: lugar?.id)
            aoSalvar(salvo)
            fechar()
        } catch {
            self.erro = error.localizedDescription
        }
    }
}
