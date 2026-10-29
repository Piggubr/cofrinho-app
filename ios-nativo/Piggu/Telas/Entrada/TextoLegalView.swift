import SwiftUI

/// Termos de Uso e Aviso de Privacidade, com o mesmo texto do site (features/legal). O texto
/// vem de Recursos/{Termos,Privacidade}.md, gerados por scripts/textos-legais.py a partir do
/// HTML do site; o CI confere que continuam iguais.
struct TextoLegalView: View {
    enum Documento: String, Identifiable, Sendable {
        case termos = "Termos"
        case privacidade = "Privacidade"

        var id: String { rawValue }

        var titulo: LocalizedStringKey {
            switch self {
            case .termos: "Termos de Uso"
            case .privacidade: "Aviso de Privacidade"
            }
        }

        var noSite: URL {
            URL(string: "https://piggu.com.br/\(rawValue.lowercased())")!
        }
    }

    let documento: Documento

    @Environment(\.paleta) private var paleta

    var body: some View {
        Group {
            if let blocos = Self.blocos(documento) {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        ForEach(blocos.indices, id: \.self) { indice in
                            bloco(blocos[indice])
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 16)
                    .frame(maxWidth: 680, alignment: .leading)
                    .frame(maxWidth: .infinity)
                }
            } else {
                ContentUnavailableView {
                    Label("Não deu para abrir o texto", systemImage: "doc.text")
                } description: {
                    Text("Leia no site do Piggu.")
                } actions: {
                    Link("Abrir no site", destination: documento.noSite)
                }
            }
        }
        .background(paleta.fundo)
        .navigationTitle(documento.titulo)
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private func bloco(_ bloco: Bloco) -> some View {
        switch bloco {
        case let .titulo(texto):
            Text(texto)
                .font(.sora(.title2, peso: .bold))
                .accessibilityAddTraits(.isHeader)
        case let .secao(texto):
            Text(texto)
                .font(.sora(.headline, peso: .semibold))
                .padding(.top, 8)
                .accessibilityAddTraits(.isHeader)
        case let .item(texto):
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(verbatim: "•").foregroundStyle(paleta.primaria).accessibilityHidden(true)
                Text(texto)
            }
        case let .paragrafo(texto):
            Text(texto)
        case let .linhaDaTabela(colunas, cabecalho):
            // A tabela do site vira um cartao por linha: a primeira coluna em destaque e as
            // outras com o nome da coluna.
            VStack(alignment: .leading, spacing: 4) {
                Text(colunas.first ?? "").font(.sora(.subheadline, peso: .semibold))
                ForEach(Array(colunas.dropFirst().enumerated()), id: \.offset) { indice, coluna in
                    Text("\(Text(cabecalho[safe: indice + 1] ?? "").bold()): \(coluna)")
                        .font(.sora(.footnote))
                        .foregroundStyle(paleta.textoSuave)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(paleta.superficie, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .accessibilityElement(children: .combine)
        }
    }

    // MARK: - Texto

    enum Bloco: Equatable {
        case titulo(AttributedString)
        case secao(AttributedString)
        case item(AttributedString)
        case paragrafo(AttributedString)
        case linhaDaTabela([AttributedString], cabecalho: [AttributedString])
    }

    nonisolated static func blocos(_ documento: Documento, pacote: Bundle = .main) -> [Bloco]? {
        guard let url = pacote.url(forResource: documento.rawValue, withExtension: "md"),
              let texto = try? String(contentsOf: url, encoding: .utf8) else {
            return nil
        }
        return blocos(de: texto)
    }

    /// O Markdown curto do script: #, ##, -, |# (cabecalho de tabela), | e paragrafos.
    nonisolated static func blocos(de texto: String) -> [Bloco] {
        var cabecalho: [AttributedString] = []
        return texto.components(separatedBy: "\n\n").compactMap { pedaco in
            let linha = pedaco.trimmingCharacters(in: .whitespacesAndNewlines)
            if linha.isEmpty || linha.hasPrefix("<!--") {
                return nil
            }
            if linha.hasPrefix("|#") {
                cabecalho = colunas(linha.dropFirst(2))
                return nil
            }
            if linha.hasPrefix("|") {
                return .linhaDaTabela(colunas(linha.dropFirst()), cabecalho: cabecalho)
            }
            if linha.hasPrefix("## ") {
                return .secao(markdown(linha.dropFirst(3)))
            }
            if linha.hasPrefix("# ") {
                return .titulo(markdown(linha.dropFirst(2)))
            }
            if linha.hasPrefix("- ") {
                return .item(markdown(linha.dropFirst(2)))
            }
            return .paragrafo(markdown(linha))
        }
    }

    nonisolated private static func colunas(_ linha: Substring) -> [AttributedString] {
        linha.components(separatedBy: " | ").map { markdown($0.trimmingCharacters(in: .whitespaces)) }
    }

    nonisolated private static func markdown<Texto: StringProtocol>(_ texto: Texto) -> AttributedString {
        (try? AttributedString(markdown: String(texto), options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)))
            ?? AttributedString(String(texto))
    }
}

private extension Array {
    subscript(safe indice: Int) -> Element? {
        indices.contains(indice) ? self[indice] : nil
    }
}
