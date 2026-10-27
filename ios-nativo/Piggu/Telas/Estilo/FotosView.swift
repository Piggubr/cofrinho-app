import SwiftUI
import UIKit

/// Fotos do mes (features/feed): as fotos que contam o que a familia viveu, com legenda. Enviar e
/// do Premium; ver, legendar e apagar, de todos.
struct FotosView: View {
    @Environment(Sessao.self) private var sessao
    @Environment(MonitorDeRede.self) private var rede
    @Environment(\.paleta) private var paleta
    @State private var mes = Date()
    @State private var carga: Carga<[FotoDoFeed]> = .carregando
    @State private var legendas: [String: String] = [:]
    @State private var enviando = false
    @State private var apagando: FotoDoFeed?
    @State private var erro: String?

    private var ehPremium: Bool { sessao.usuario?.plano == .premium || sessao.usuario?.role == .admin }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                if let erro { Faixa(texto: erro, tipo: .erro) }
                Cartao(nil) {
                    if ehPremium {
                        if enviando {
                            ProgressView("Enviando...")
                        } else {
                            BotoesDeFoto(tituloDaGaleria: "Adicionar foto") { imagem in Task { await enviar(imagem) } }
                        }
                    } else {
                        LinkDoSite(titulo: "Adicionar fotos · Premium", caminho: "/plano")
                    }
                }
                switch carga {
                case .carregando:
                    ProgressView().frame(maxWidth: .infinity, minHeight: 120)
                case let .falhou(erro):
                    EstadoDeErro(erro: erro) { Task { await carregar() } }
                case let .pronto(fotos, _):
                    if fotos.isEmpty {
                        EstadoVazio(icone: "photo", titulo: "Nenhuma foto neste mês ainda",
                                    texto: "As fotos do mês contam a história do que vocês viveram.")
                    } else {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 160), spacing: 12)], spacing: 12) {
                            ForEach(fotos) { cartao($0) }
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
        }
        .background(paleta.fundo)
        .safeAreaInset(edge: .top) { SeletorDeMes(mes: $mes) }
        .navigationTitle("Fotos do mês")
        .refreshable { await carregar() }
        .task(id: "\(Formatos.mes(mes))#\(rede.conectado)") { await carregar() }
        .confirmationDialog("Apagar esta foto?", isPresented: Binding(get: { apagando != nil }, set: { if !$0 { apagando = nil } }),
                            titleVisibility: .visible, presenting: apagando) { foto in
            Button("Apagar", role: .destructive) { Task { await excluir(foto) } }
        }
    }

    private func cartao(_ foto: FotoDoFeed) -> some View {
        VStack(spacing: 8) {
            FotoDoAsset(assetId: foto.assetId)
                .aspectRatio(1, contentMode: .fill)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .accessibilityLabel(foto.legenda?.isEmpty == false ? foto.legenda! : String(localized: "Foto do mês"))
            HStack {
                TextField("Escreva uma legenda", text: Binding(
                    get: { legendas[foto.id] ?? foto.legenda ?? "" },
                    set: { legendas[foto.id] = String($0.prefix(300)) }))
                    .font(.sora(.footnote))
                    .submitLabel(.done)
                    .onSubmit { Task { await legendar(foto) } }
                    .accessibilityLabel("Legenda da foto")
                Button(role: .destructive) { apagando = foto } label: { Image(systemName: "trash") }
                    .buttonStyle(.borderless).accessibilityLabel("Apagar")
            }
        }
        .padding(10)
        .background(paleta.superficie, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func carregar() async {
        let chave = Formatos.mes(mes)
        await sessao.cache.carregar("fotos/\(chave)", mostrar: { carga = $0 }) { try await sessao.cliente.listarFeed(chave) }
    }

    private func enviar(_ imagem: UIImage) async {
        guard let jpeg = Imagem.fotoDeProduto(imagem) else {
            erro = String(localized: "Não consegui ler essa imagem.")
            return
        }
        enviando = true
        defer { enviando = false }
        do {
            let foto = try await sessao.cliente.publicarNoFeed(mes: Formatos.mes(mes), jpeg: jpeg)
            if case let .pronto(fotos, _) = carga { carga = .pronto([foto] + fotos) }
            erro = nil
        } catch {
            erro = error.localizedDescription
        }
    }

    /// Cada legenda e salva sozinha, ao terminar de escrever.
    private func legendar(_ foto: FotoDoFeed) async {
        guard let legenda = legendas[foto.id], legenda != foto.legenda else { return }
        do {
            let atualizada = try await sessao.cliente.legendar(foto.id, legenda)
            if case let .pronto(fotos, guardadoEm) = carga {
                carga = .pronto(fotos.map { $0.id == atualizada.id ? atualizada : $0 }, guardadoEm: guardadoEm)
            }
            erro = nil
        } catch {
            erro = error.localizedDescription
        }
    }

    private func excluir(_ foto: FotoDoFeed) async {
        do {
            try await sessao.cliente.excluirDoFeed(foto.id)
            if case let .pronto(fotos, _) = carga { carga = .pronto(fotos.filter { $0.id != foto.id }) }
        } catch {
            erro = error.localizedDescription
        }
    }
}
