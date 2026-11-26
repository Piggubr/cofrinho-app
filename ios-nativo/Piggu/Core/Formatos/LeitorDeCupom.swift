import CoreGraphics
import Foundation

/// Le o total, a data e os itens de um cupom fiscal a partir das linhas que a camera reconheceu.
/// Nao chuta: sem uma linha de total, devolve nil e a pessoa digita.
enum LeitorDeCupom {
    struct Leitura: Equatable, Sendable {
        var total: Decimal?
        /// aaaa-mm-dd
        var data: String?
        var itens: [Item] = []
    }

    struct Item: Equatable, Sendable, Identifiable {
        var id = UUID()
        var descricao: String
        var valor: Decimal

        static func == (a: Item, b: Item) -> Bool { a.descricao == b.descricao && a.valor == b.valor }
    }

    /// Um bloco de texto que a camera achou, com os quatro cantos (topo esquerdo, topo direito,
    /// baixo direito, baixo esquerdo) em qualquer sistema de coordenadas, o mesmo para todos.
    struct Trecho: Sendable {
        var texto: String
        var cantos: [CGPoint]
    }

    /// Da mais forte para a mais fraca: "VALOR A PAGAR" ganha de "TOTAL".
    private static let chaves = ["VALOR A PAGAR", "TOTAL A PAGAR", "VALOR PAGO", "VALOR TOTAL", "TOTAL R$", "TOTAL"]
    /// Linhas com "total" que nao sao o total da compra (o "Val.Total" e a tabela do IVA em Portugal).
    private static let falsas = ["SUBTOTAL", "SUB TOTAL", "TOTAL DE ITENS", "QTD", "TRIBUTO", "IMPOSTO", "LEI 12.741", "DESCONTO",
                                 "VAL.TOTAL", "VAL. TOTAL"]
    /// Onde a lista de itens acaba.
    private static let fimDosItens = ["TOTAL", "A PAGAR", "TROCO"]
    /// Palavras de linhas com valor que nao sao item (palavra inteira: "oliva" nao e "IVA").
    private static let naoItens: Set<String> = ["CNPJ", "CPF", "NIF", "IVA", "TRIBUTO", "TRIBUTOS", "IMPOSTO", "IMPOSTOS", "TROCO"]
    /// Palavras da linha que tira do item de cima ("Poupanca Lidl Plus -0,80", "Desconto 2,00").
    private static let descontos: Set<String> = ["DESC", "DESCONTO", "POUPANCA", "PROMO", "PROMOCAO"]
    /// Pedacos do fim da descricao que sao quantidade ou unidade ("1 UN X 24,90", "x 8").
    private static let unidades: Set<String> = ["X", "UN", "UND", "UNID", "KG", "G", "L", "*", "R$", "€", "EUR"]

    static func ler(_ linhas: [String], hoje: Date = Date()) -> Leitura {
        let normais = linhas.map(normalizar)
        let itens = itens(linhas, normais: normais)
        return Leitura(total: total(normais), data: data(normais, hoje: hoje), itens: itens)
    }

    static func ler(_ trechos: [Trecho], hoje: Date = Date()) -> Leitura {
        ler(linhas(trechos), hoje: hoje)
    }

    /// Remonta as linhas do cupom: a camera le a coluna dos nomes e a dos precos em blocos
    /// separados, e o total so faz sentido com o nome e o valor na mesma linha. Junta o que esta
    /// na mesma altura, seguindo a inclinacao do papel, de cima para baixo.
    static func linhas(_ trechos: [Trecho]) -> [String] {
        struct Pedaco { let texto: String; let centro: CGPoint; let direita: CGPoint; let descida: CGPoint }
        var pedacos: [Pedaco] = []
        for trecho in trechos where trecho.cantos.count == 4 {
            let (tl, tr, br, bl) = (trecho.cantos[0], trecho.cantos[1], trecho.cantos[2], trecho.cantos[3])
            let partes = trecho.texto.components(separatedBy: .newlines).filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
            // Bloco de varias linhas: divide a altura por igual.
            for (i, parte) in partes.enumerated() {
                let a = CGFloat(i) / CGFloat(partes.count), b = CGFloat(i + 1) / CGFloat(partes.count)
                let cantos = [mistura(tl, bl, a), mistura(tr, br, a), mistura(tr, br, b), mistura(tl, bl, b)]
                pedacos.append(Pedaco(texto: parte,
                                      centro: CGPoint(x: cantos.map(\.x).reduce(0, +) / 4, y: cantos.map(\.y).reduce(0, +) / 4),
                                      direita: CGPoint(x: tr.x - tl.x, y: tr.y - tl.y),
                                      descida: CGPoint(x: cantos[3].x - cantos[0].x, y: cantos[3].y - cantos[0].y)))
            }
        }
        guard !pedacos.isEmpty else { return [] }
        // A direcao da linha e a media de todas; a normal aponta para baixo do texto.
        let soma = pedacos.reduce(CGPoint.zero) { CGPoint(x: $0.x + $1.direita.x, y: $0.y + $1.direita.y) }
        let tamanho = max(hypot(soma.x, soma.y), .ulpOfOne)
        let direita = CGPoint(x: soma.x / tamanho, y: soma.y / tamanho)
        var normal = CGPoint(x: -direita.y, y: direita.x)
        if pedacos.reduce(0, { $0 + escalar($1.descida, normal) }) < 0 { normal = CGPoint(x: -normal.x, y: -normal.y) }
        let alturas = pedacos.map { abs(escalar($0.descida, normal)) }.sorted()
        let tolerancia = alturas[alturas.count / 2] * 0.5

        var linhas: [[Pedaco]] = []
        var alturaDaLinha: CGFloat = 0
        for pedaco in pedacos.sorted(by: { escalar($0.centro, normal) < escalar($1.centro, normal) }) {
            let y = escalar(pedaco.centro, normal)
            if let ultima = linhas.indices.last, abs(y - alturaDaLinha) <= tolerancia {
                linhas[ultima].append(pedaco)
                alturaDaLinha = linhas[ultima].map { escalar($0.centro, normal) }.reduce(0, +) / CGFloat(linhas[ultima].count)
            } else {
                linhas.append([pedaco])
                alturaDaLinha = y
            }
        }
        return linhas.map { $0.sorted { escalar($0.centro, direita) < escalar($1.centro, direita) }.map(\.texto).joined(separator: " ") }
    }

    private static func mistura(_ a: CGPoint, _ b: CGPoint, _ t: CGFloat) -> CGPoint {
        CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t)
    }

    private static func escalar(_ a: CGPoint, _ b: CGPoint) -> CGFloat { a.x * b.x + a.y * b.y }

    private static func normalizar(_ linha: String) -> String {
        linha.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Formatos.localidade).uppercased()
    }

    private static func total(_ linhas: [String]) -> Decimal? {
        for chave in chaves {
            for (indice, linha) in linhas.enumerated() where linha.contains(chave) && !falsas.contains(where: linha.contains) {
                // O valor fica na mesma linha (o ultimo dela) ou, em cupom de duas colunas, sozinho na de baixo.
                let abaixo = indice + 1 < linhas.count && !linhas[indice + 1].contains(where: \.isLetter) ? valores(linhas[indice + 1]).first : nil
                if let valor = valores(linha).last ?? abaixo {
                    return valor
                }
            }
        }
        return nil
    }

    /// Cada linha com descricao e valor no fim, ate o total. O desconto logo abaixo sai do item.
    private static func itens(_ linhas: [String], normais: [String]) -> [Item] {
        let padrao = /(-?)\s*((?:\d{1,3}(?:\.\d{3})+|\d+)[,.]\d{2})(?!\d)\s*(?:[A-Za-z]{1,2}|R\$|€)?\s*$/
        var itens: [Item] = []
        for (linha, normal) in zip(linhas, normais) {
            if fimDosItens.contains(where: normal.contains) { break }
            let palavras = Set(normal.split { !$0.isLetter && !$0.isNumber }.map(String.init))
            guard naoItens.isDisjoint(with: palavras),
                  let achado = linha.firstMatch(of: padrao), let valor = Formatos.lerValor(String(achado.output.2)), valor > 0 else { continue }
            let negativo = !achado.output.1.isEmpty
            let descricao = descricao(String(linha[..<achado.range.lowerBound]))
            if negativo || !descontos.isDisjoint(with: palavras) {
                if let ultimo = itens.indices.last, itens[ultimo].valor > valor { itens[ultimo].valor -= valor }
            } else if descricao.filter(\.isLetter).count >= 2 {
                itens.append(Item(descricao: descricao, valor: valor))
            }
        }
        return itens
    }

    /// "001 7891234 ARROZ 5KG 1 UN X 24,90" vira "Arroz 5kg": tira o codigo do comeco e a
    /// quantidade do fim.
    private static func descricao(_ texto: String) -> String {
        var partes = texto.split(whereSeparator: \.isWhitespace).map(String.init)
        while let primeira = partes.first, primeira.allSatisfy(\.isNumber) { partes.removeFirst() }
        while let ultima = partes.last, ultima.allSatisfy({ $0.isNumber || ",.".contains($0) }) || unidades.contains(ultima.uppercased()) {
            partes.removeLast()
        }
        let junto = partes.joined(separator: " ").lowercased(with: Formatos.localidade)
        return junto.prefix(1).uppercased() + junto.dropFirst().prefix(199)
    }

    /// "12,50", "1.234,56" e, em cupom sem virgula, "12.50".
    static func valores(_ linha: String) -> [Decimal] {
        let padrao = /(\d{1,3}(?:\.\d{3})+|\d+)[,.]\d{2}(?!\d)/
        return linha.matches(of: padrao).compactMap { Formatos.lerValor(String($0.output.0)) }.filter { $0 > 0 }
    }

    private static func data(_ linhas: [String], hoje: Date) -> String? {
        let padrao = /(\d{2})\/(\d{2})\/(\d{4}|\d{2})(?!\d)/
        let ultima = Formatos.dia(hoje)
        for linha in linhas {
            for achado in linha.matches(of: padrao) {
                guard let dia = Int(achado.output.1), let mes = Int(achado.output.2), var ano = Int(achado.output.3) else { continue }
                if ano < 100 { ano += 2000 }
                let texto = String(format: "%04d-%02d-%02d", ano, mes, dia)
                // So data que existe e ja aconteceu.
                if let data = Formatos.data(deDia: texto), Formatos.dia(data) == texto, texto <= ultima {
                    return texto
                }
            }
        }
        return nil
    }
}
