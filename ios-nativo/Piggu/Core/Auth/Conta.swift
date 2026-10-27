import Foundation

/// Regras da conta conferidas no app antes de mandar; o backend confere de novo.
/// Espelho de core/auth/regras-da-senha.ts, core/ui/email.ts, core/ui/nascimento.ts e
/// core/privacidade/aviso.ts.
enum Conta {
    /// Versao vigente dos Termos e do Aviso de Privacidade. Igual a VERSAO_DO_AVISO do site e a
    /// Consentimentos.VERSAO_DO_AVISO do backend: mudou o texto, muda nos tres.
    static let versaoDosTermos = "2026-10-16"
    /// Canal do encarregado (LGPD art. 41).
    static let contatoDePrivacidade = "privacidade@piggu.com.br"
    static let minimoDaSenha = 8
    private static let maximoDaSenhaEmBytes = 72
    /// Idade minima para ter conta (LGPD art. 14).
    static let idadeMinima = 13

    /// O que falta na senha, ou nil quando ela serve (Senhas.java no backend).
    static func problemaDaSenha(_ senha: String, email: String = "") -> String? {
        // Como o site: o tamanho conta em unidades UTF-16, o teto em bytes.
        if senha.utf16.count < minimoDaSenha {
            return String(localized: "A senha precisa ter pelo menos \(minimoDaSenha) caracteres.")
        }
        if senha.utf8.count > maximoDaSenhaEmBytes {
            return String(localized: "A senha pode ter no máximo \(maximoDaSenhaEmBytes) caracteres.")
        }
        let escalares = senha.unicodeScalars
        // \p{L} e \p{Nd}, como no site.
        let letras: Set<Unicode.GeneralCategory> = [
            .uppercaseLetter, .lowercaseLetter, .titlecaseLetter, .modifierLetter, .otherLetter,
        ]
        guard escalares.contains(where: { letras.contains($0.properties.generalCategory) }),
              escalares.contains(where: { $0.properties.generalCategory == .decimalNumber }) else {
            return String(localized: "Use letras e números na senha.")
        }
        let limpo = { (texto: String) in texto.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
        if !email.isEmpty && limpo(senha) == limpo(email) {
            return String(localized: "A senha não pode ser o seu e-mail.")
        }
        return nil
    }

    /// Conferencia rapida: um "@" so, sem espaco, e um ponto no meio do dominio.
    static func pareceEmail(_ texto: String) -> Bool {
        let partes = texto.split(separator: "@", omittingEmptySubsequences: false)
        guard partes.count == 2, !partes[0].isEmpty, !texto.contains(where: \.isWhitespace) else {
            return false
        }
        // Ponto com pelo menos um caractere antes e um depois, dentro do dominio.
        return partes[1].dropFirst().dropLast().contains(".")
    }

    /// Mensagem para a data escolhida, ou nil quando esta boa.
    static func problemaDoNascimento(_ data: Date?, hoje: Date = .now, calendario: Calendar = .current) -> String? {
        guard let data else {
            return String(localized: "Informe sua data de nascimento.")
        }
        let anos = calendario.dateComponents([.year], from: data, to: hoje).year ?? 0
        if data > hoje || anos > 120 {
            return String(localized: "Confira a data de nascimento.")
        }
        if anos < idadeMinima {
            return String(localized: "O Piggu é para quem tem \(idadeMinima) anos ou mais.")
        }
        return nil
    }
}

/// CPF: mascara enquanto digita e os digitos verificadores (core/ui/cpf.ts).
enum CPF {
    static let exemplo = "000.000.000-00"

    /// So os digitos, no maximo 11.
    static func digitos(_ texto: String) -> String {
        String(texto.filter(\.isASCII).filter(\.isNumber).prefix(11))
    }

    /// "52998224725" -> "529.982.247-25", ja no meio da digitacao.
    static func formatar(_ texto: String) -> String {
        let d = Array(digitos(texto))
        var saida = ""
        for (indice, digito) in d.enumerated() {
            if indice == 3 || indice == 6 { saida.append(".") }
            if indice == 9 { saida.append("-") }
            saida.append(digito)
        }
        return saida
    }

    /// Digitos verificadores da Receita; numeros todos iguais nao valem.
    static func valido(_ texto: String) -> Bool {
        let d = digitos(texto).compactMap(\.wholeNumberValue)
        guard d.count == 11, Set(d).count > 1 else { return false }
        func digito(_ posicao: Int) -> Int {
            let soma = (0..<posicao).reduce(0) { $0 + d[$1] * (posicao + 1 - $1) }
            let resto = soma % 11
            return resto < 2 ? 0 : 11 - resto
        }
        return digito(9) == d[9] && digito(10) == d[10]
    }
}
