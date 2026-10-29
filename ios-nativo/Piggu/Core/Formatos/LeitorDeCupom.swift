import Foundation

/// Le o total e a data de um cupom fiscal a partir das linhas que a camera reconheceu. Nao chuta:
/// sem uma linha de total, devolve nil e a pessoa digita.
enum LeitorDeCupom {
    struct Leitura: Equatable, Sendable {
        var total: Decimal?
        /// aaaa-mm-dd
        var data: String?
    }

    /// Da mais forte para a mais fraca: "VALOR A PAGAR" ganha de "TOTAL".
    private static let chaves = ["VALOR A PAGAR", "TOTAL A PAGAR", "VALOR PAGO", "VALOR TOTAL", "TOTAL R$", "TOTAL"]
    /// Linhas com "total" que nao sao o total da compra.
    private static let falsas = ["SUBTOTAL", "SUB TOTAL", "TOTAL DE ITENS", "QTD", "TRIBUTO", "IMPOSTO", "LEI 12.741", "DESCONTO"]

    static func ler(_ linhas: [String], hoje: Date = Date()) -> Leitura {
        let normais = linhas.map { $0.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Formatos.localidade).uppercased() }
        return Leitura(total: total(normais), data: data(normais, hoje: hoje))
    }

    private static func total(_ linhas: [String]) -> Decimal? {
        for chave in chaves {
            for (indice, linha) in linhas.enumerated() where linha.contains(chave) && !falsas.contains(where: linha.contains) {
                // O valor fica na mesma linha (o ultimo dela) ou, em cupom de duas colunas, na de baixo.
                if let valor = valores(linha).last ?? (indice + 1 < linhas.count ? valores(linhas[indice + 1]).first : nil) {
                    return valor
                }
            }
        }
        return nil
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
