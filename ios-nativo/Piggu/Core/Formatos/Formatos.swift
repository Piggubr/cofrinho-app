import Foundation

/// Formatos do Brasil: R$, virgula decimal, datas por extenso.
enum Formatos {
    static let localidade = Locale(identifier: "pt_BR")

    private static var calendario: Calendar {
        var calendario = Calendar(identifier: .gregorian)
        calendario.locale = localidade
        calendario.timeZone = .current
        return calendario
    }

    /// Moeda da pessoa logada (preferencias.moeda); a Sessao troca ao entrar.
    // shortcut: global mutavel lido fora do MainActor, escrito so no login; vira Environment se as telas trocarem de moeda ao vivo.
    nonisolated(unsafe) static var codigoDaMoeda = "BRL"

    /// - Parameter codigo: moeda fixa do valor (saldo de banco, compra em outra moeda); sem ela, a da pessoa.
    static func moeda(_ valor: Decimal, codigo: String? = nil) -> String {
        valor.formatted(.currency(code: codigo ?? codigoDaMoeda).locale(localidade))
    }

    /// Simbolo curto da moeda da pessoa (R$, €, US$), para o rotulo do campo de valor.
    static var simboloDaMoeda: String {
        Locale(identifier: "pt_BR@currency=\(codigoDaMoeda)").currencySymbol ?? codigoDaMoeda
    }

    /// "Euro (EUR)"
    static func nomeDaMoeda(_ codigo: String) -> String {
        localidade.localizedString(forCurrencyCode: codigo).map { "\($0) (\(codigo))" } ?? codigo
    }

    /// Cotacao do topo: "1 EUR = R$ 6,12"; taxa menor que 1 ganha ate quatro casas.
    static func cotacao(_ cotacao: Cotacao) -> String {
        let casas = cotacao.taxa < 1 ? 2...4 : 2...2
        let valor = cotacao.taxa.formatted(
            .currency(code: cotacao.para).precision(.fractionLength(casas)).locale(localidade))
        return "1 \(cotacao.de) = \(valor)"
    }

    /// - Parameter valor: ja em porcentagem (12,5 e 12,5%), como manda o backend.
    static func porcentagem(_ valor: Decimal) -> String {
        (valor / 100).formatted(.percent.precision(.fractionLength(0...1)).locale(localidade))
    }

    /// Le o valor digitado: aceita virgula ou ponto decimal, ponto de milhar e "R$".
    ///
    /// "12,50" e 12.5; "1.234,56" e 1234.56; "1.234" e 1234 (no Brasil o ponto sozinho
    /// com tres digitos e milhar); "12.5" e 12.5. Mais de duas casas decimais, texto
    /// vazio ou qualquer letra devolve nil.
    static func lerValor(_ texto: String) -> Decimal? {
        var limpo = texto.replacingOccurrences(of: "R$", with: "")
        limpo.removeAll { $0.isWhitespace }
        guard !limpo.isEmpty, limpo.allSatisfy({ $0.isASCII && ($0.isNumber || $0 == "," || $0 == ".") }) else {
            return nil
        }

        let ultimaVirgula = limpo.lastIndex(of: ",")
        let ultimoPonto = limpo.lastIndex(of: ".")
        let inteiro: String
        let fracao: String

        switch (ultimaVirgula, ultimoPonto) {
        case let (virgula?, ponto?):
            // A ultima marca e o decimal; a outra e milhar.
            let decimal = max(virgula, ponto)
            let milhar: Character = decimal == virgula ? "." : ","
            inteiro = String(limpo[..<decimal]).replacingOccurrences(of: String(milhar), with: "")
            fracao = String(limpo[limpo.index(after: decimal)...])
            guard !inteiro.contains(limpo[decimal]) else { return nil }
        case let (virgula?, nil):
            guard limpo.filter({ $0 == "," }).count == 1 else { return nil }
            inteiro = String(limpo[..<virgula])
            fracao = String(limpo[limpo.index(after: virgula)...])
        case let (nil, ponto?):
            let grupos = limpo.split(separator: ".", omittingEmptySubsequences: false)
            if grupos.count > 1, grupos.dropFirst().allSatisfy({ $0.count == 3 }), !grupos[0].isEmpty, grupos[0] != "0" {
                inteiro = grupos.joined()
                fracao = ""
            } else if grupos.count == 2 {
                inteiro = String(limpo[..<ponto])
                fracao = String(limpo[limpo.index(after: ponto)...])
            } else {
                return nil
            }
        case (nil, nil):
            inteiro = limpo
            fracao = ""
        }

        guard fracao.count <= 2, !(inteiro.isEmpty && fracao.isEmpty),
              inteiro.allSatisfy(\.isNumber), fracao.allSatisfy(\.isNumber) else {
            return nil
        }
        let normalizado = (inteiro.isEmpty ? "0" : inteiro) + (fracao.isEmpty ? "" : "." + fracao)
        return Decimal(string: normalizado, locale: Locale(identifier: "en_US_POSIX"))
    }

    /// 12.5 vira "12,50", para pre-preencher o campo de valor.
    static func valorParaDigitar(_ valor: Decimal) -> String {
        valor.formatted(.number.grouping(.never).precision(.fractionLength(2)).locale(localidade))
    }

    /// aaaa-mm, o formato do parametro `mes` da API.
    static func mes(_ data: Date) -> String {
        let partes = calendario.dateComponents([.year, .month], from: data)
        return String(format: "%04d-%02d", partes.year ?? 0, partes.month ?? 0)
    }

    /// aaaa-mm-dd, o LocalDate do backend.
    static func dia(_ data: Date) -> String {
        let partes = calendario.dateComponents([.year, .month, .day], from: data)
        return String(format: "%04d-%02d-%02d", partes.year ?? 0, partes.month ?? 0, partes.day ?? 0)
    }

    static func data(deDia texto: String) -> Date? {
        let partes = texto.split(separator: "-").compactMap { Int($0) }
        guard partes.count == 3 else { return nil }
        return calendario.date(from: DateComponents(year: partes[0], month: partes[1], day: partes[2]))
    }

    /// "outubro de 2026"
    static func nomeDoMes(_ data: Date) -> String {
        data.formatted(.dateTime.month(.wide).year().locale(localidade))
    }

    /// "sexta-feira, 23 de outubro"
    static func diaPorExtenso(_ texto: String) -> String {
        guard let data = data(deDia: texto) else { return texto }
        return data.formatted(.dateTime.weekday(.wide).day().month(.wide).locale(localidade))
    }

    /// "outubro de 2026" vira "Outubro de 2026": so a primeira letra, como se escreve em portugues.
    static func inicialMaiuscula(_ texto: String) -> String {
        texto.prefix(1).uppercased(with: localidade) + texto.dropFirst()
    }

    /// "26/10/2026", o dataBr do site.
    static func dataCurta(_ texto: String) -> String {
        guard let data = data(deDia: texto) else { return texto }
        return data.formatted(.dateTime.day(.twoDigits).month(.twoDigits).year().locale(localidade))
    }

    /// "outubro de 2026" a partir de "2026-10".
    static func nomeDoMes(chave: String) -> String {
        data(deDia: chave + "-01").map { inicialMaiuscula(nomeDoMes($0)) } ?? chave
    }

    static func somarMeses(_ meses: Int, a data: Date) -> Date {
        calendario.date(byAdding: .month, value: meses, to: data) ?? data
    }
}
