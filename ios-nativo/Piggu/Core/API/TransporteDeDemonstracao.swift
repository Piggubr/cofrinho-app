#if DEBUG
import Foundation

/// Responde como a API, sem rede, com dados inventados. So existe em Debug: serve para
/// o preview do SwiftUI e para ver as telas no simulador com `-PigguDemo YES`.
/// Qualquer e-mail e senha entram; `-PigguDemoLogado YES` ja abre logado.
///
/// `-PigguDemoMuitos 3000` enche o mes de gastos para medir o desempenho; `-PigguDemoCache YES`
/// guarda o cache num arquivo da demonstracao, para medir a abertura com cache.
/// `-PigguDemoFalha rede|servidor|vazio` simula, fora do login, a falta de internet, o erro
/// 500 e o mes sem nenhum gasto, para ver os estados das telas.
///
/// `-PigguDemoConta termos|sobre-voce|cpf|email` abre a conta com o que falta nela: os Termos
/// mudaram, sem apelido e nascimento, sem CPF ou com o e-mail sem confirmar; `premium` abre no
/// Premium (relatorio do ano, limite por categoria, conectar banco); `membro`, como membro da familia. Na demonstracao,
/// a Apple e o Google sempre criam conta nova (pedem o aceite), e o token "vencido" nos links
/// do e-mail responde LINK_INVALIDO.
actor TransporteDeDemonstracao: Transporte {
    enum Falha: String {
        case rede, servidor, vazio
    }

    private var logado: Bool
    private var gastos: [[String: Any]]
    private let falha: Falha?
    private var usuario = TransporteDeDemonstracao.usuarioPadrao
    /// A conta `premium` assinou pelo site; a compra no app (o .storekit) troca para a App Store.
    private var origemDoPremium = "WEB"
    private var dados = DadosDaDemonstracao()
    /// -PigguDemoMuitos 3000: o mes com milhares de gastos, para medir a lista e o painel. Sao
    /// gerados no primeiro pedido, fora da thread principal, como se viessem do servidor.
    private var muitosPorGerar = UserDefaults.standard.integer(forKey: "PigguDemoMuitos")

    init(
        logado: Bool = false,
        falha: Falha? = UserDefaults.standard.string(forKey: "PigguDemoFalha").flatMap(Falha.init),
        conta: String? = UserDefaults.standard.string(forKey: "PigguDemoConta")
    ) {
        self.logado = logado
        self.falha = falha
        switch conta {
        case "termos":
            usuario["termosAceitos"] = "2026-01-01"
        case "sobre-voce":
            usuario["apelido"] = NSNull()
            usuario["nascimento"] = NSNull()
            usuario["cpfInformado"] = false
            usuario["celularInformado"] = false
            usuario["cadastroCompleto"] = false
        case "cpf":
            usuario["cpfInformado"] = false
        case "email":
            usuario["emailConfirmado"] = false
        case "premium":
            usuario["plano"] = "PREMIUM"
            usuario["premiumAte"] = "2027-10-27T03:00:00Z"
        case "membro":
            usuario["role"] = "MEMBRO"
        default:
            break
        }
        let hoje = Date()
        func dia(_ antes: Int) -> String {
            Formatos.dia(Calendar.current.date(byAdding: .day, value: -antes, to: hoje) ?? hoje)
        }
        // Sempre dentro do mes corrente, para o painel nao ficar vazio no dia 1.
        let diaDoMes = Calendar.current.component(.day, from: hoje)
        func noMes(_ antes: Int) -> String { dia(min(antes, diaDoMes - 1)) }

        gastos = [
            Self.gasto("Mercado da semana", "Mercado", 312.47, noMes(0), "Pão de Açúcar", conta: DadosDaDemonstracao.cartao),
            Self.gasto("Uber para o trabalho", "Transporte", 23.90, noMes(1), "Uber", conta: DadosDaDemonstracao.banco),
            Self.gasto("Almoço", "Alimentação", 42.00, noMes(1), "Restaurante da esquina", conta: DadosDaDemonstracao.banco),
            Self.gasto("Conta de luz", "Casa", 189.32, noMes(3), "Enel", conta: DadosDaDemonstracao.banco,
                       compartilhado: true),
            Self.gasto("Cinema", "Lazer", 64.00, noMes(5), "Cinemark", conta: DadosDaDemonstracao.cartao,
                       usuario: DadosDaDemonstracao.parceira, compartilhado: true),
            Self.gasto("Farmácia", "Saúde", 37.80, noMes(6), "Drogasil"),
            Self.gasto("Fone de ouvido", "Lazer", 149.90, noMes(7), "Fnac", conta: DadosDaDemonstracao.cartao,
                       parcela: (2, 6)),
            Self.gasto("Gasolina", "Transporte", 220.00, noMes(8), "Posto Shell", conta: DadosDaDemonstracao.cartao),
        ]
    }

    private func gerarMuitos() {
        let hoje = Date()
        let diaDoMes = Calendar.current.component(.day, from: hoje)
        let dias = (0..<diaDoMes).map { Formatos.dia(Calendar.current.date(byAdding: .day, value: -$0, to: hoje) ?? hoje) }
        let categorias = ["Mercado", "Transporte", "Alimentação", "Casa", "Lazer", "Saúde"]
        gastos += (0..<muitosPorGerar).map { i in
            Self.gasto("Gasto \(i + 1)", categorias[i % categorias.count], Double(i % 200) + 9.9,
                       dias[i % dias.count], "Loja \(i % 50)")
        }
        muitosPorGerar = 0
    }

    private static func gasto(
        _ item: String, _ categoria: String, _ valor: Double, _ data: String, _ estabelecimento: String,
        conta: String? = nil, usuario: String? = nil, compartilhado: Bool = false, parcela: (Int, Int)? = nil
    ) -> [String: Any] {
        [
            "contaId": conta as Any? ?? NSNull(),
            "parcela": parcela?.0 as Any? ?? NSNull(),
            "parcelas": parcela?.1 as Any? ?? NSNull(),
            "id": UUID().uuidString.lowercased(),
            "data": data,
            "estabelecimento": estabelecimento,
            "item": item,
            "categoria": categoria,
            "valor": valor,
            "tipo": "Variavel",
            "origem": "Manual",
            "usuario": usuario ?? Self.usuarioPadrao["id"] as Any,
            "registradoEm": ISO8601DateFormatter().string(from: Date()),
            "compartilhado": compartilhado,
        ]
    }

    static let idDaPessoa = "8a5c2f0e-0000-4000-8000-000000000001"

    nonisolated(unsafe) private static let usuarioPadrao: [String: Any] = [
        "id": "8a5c2f0e-0000-4000-8000-000000000001",
        "email": "demo@piggu.app",
        "nome": "Pessoa de Demonstração",
        "primeiroNome": "Pessoa",
        "apelido": "Pessoa",
        "foto": NSNull(),
        "role": "TITULAR",
        "ativo": true,
        "plano": "GRATUITO",
        "premiumAte": NSNull(),
        "familia": "8a5c2f0e-0000-4000-8000-0000000000f1",
        "emailConfirmado": true,
        "pais": "BR",
        "nascimento": "1990-01-01",
        "cpfInformado": true,
        "celularInformado": true,
        "cadastroCompleto": true,
        "termosAceitos": Conta.versaoDosTermos,
        "temSenha": true,
        "preferencias": [
            "moeda": "BRL", "moedaCotacao": "USD", "moedaConversao": "BRL", "mostrarCotacao": true,
        ],
        "compartilhamento": ["mostraTudoAoParceiro": true, "compartilharPorPadrao": false],
        "avisos": ["email": true, "push": false],
        "fotoPropria": false,
        "temFotoDoGoogle": false,
    ]

    func enviar(_ pedido: URLRequest) async throws -> (Data, HTTPURLResponse) {
        if muitosPorGerar > 0 {
            gerarMuitos()
        }
        try await Task.sleep(for: .milliseconds(250))
        let url = pedido.url!
        let caminho = url.path.replacingOccurrences(of: "/api", with: "")
        let metodo = pedido.httpMethod ?? "GET"

        let corpo = pedido.httpBody.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] } ?? [:]

        if !caminho.hasPrefix("/auth") {
            switch falha {
            case .rede:
                throw URLError(.notConnectedToInternet)
            case .servidor:
                return responder(url, 500, erro("Erro interno."))
            case .vazio:
                gastos = []
                dados.esvaziar()
            case nil:
                break
            }
        }

        switch (metodo, caminho) {
        case ("POST", "/auth/entrar"):
            logado = true
            return responder(url, 200, tokens(noCorpo: corpo["refreshNoCorpo"] as? Bool == true))
        case ("POST", "/auth/google"), ("POST", "/auth/apple"):
            guard corpo["versaoDosTermos"] as? String == Conta.versaoDosTermos else {
                return responder(url, 422, erro(
                    "Para criar sua conta, leia e aceite os Termos de Uso e o Aviso de Privacidade.",
                    codigo: "TERMOS_NECESSARIOS"))
            }
            logado = true
            // Conta nova: o nome da Apple vira o apelido; faltam nascimento, CPF e celular (D-088).
            usuario["apelido"] = corpo["nome"] as? String ?? usuario["primeiroNome"]
            usuario["nascimento"] = NSNull()
            usuario["cpfInformado"] = false
            usuario["celularInformado"] = false
            usuario["cadastroCompleto"] = false
            return responder(url, 200, tokens(noCorpo: corpo["refreshNoCorpo"] as? Bool == true))
        case ("POST", "/auth/cadastro"), ("POST", "/auth/senha/esqueci"):
            return responder(url, 202, nil)
        case ("POST", "/auth/senha/redefinir"):
            return corpo["token"] as? String == "vencido"
                ? responder(url, 422, erro("Este link venceu ou ja foi usado. Peca outro em \"Esqueci minha senha\".",
                                           codigo: "LINK_INVALIDO"))
                : responder(url, 204, nil)
        case ("POST", "/auth/confirmar-email"):
            guard corpo["token"] as? String != "vencido" else {
                return responder(url, 422, erro("Este link de confirmacao venceu ou ja foi usado. Peca outro no Perfil.",
                                                codigo: "LINK_INVALIDO"))
            }
            usuario["emailConfirmado"] = true
            return responder(url, 204, nil)
        case ("POST", "/auth/me/email/reenviar"):
            return responder(url, 204, nil)
        case ("PUT", "/me/termos"):
            usuario["termosAceitos"] = corpo["versao"]
            return responder(url, 200, usuario)
        case ("PUT", "/me/sobre-voce"):
            usuario["apelido"] = corpo["apelido"]
            if let nascimento = corpo["nascimento"] {
                usuario["nascimento"] = nascimento
            }
            if corpo["cpf"] is String { usuario["cpfInformado"] = true }
            if corpo["telefone"] is String { usuario["celularInformado"] = true }
            usuario["cadastroCompleto"] = usuario["cpfInformado"] as? Bool != false
                && usuario["celularInformado"] as? Bool != false
            return responder(url, 200, usuario)
        case ("PUT", "/me/pais"):
            usuario["pais"] = corpo["pais"]
            usuario["cpfInformado"] = corpo["documento"] is String
            return responder(url, 200, usuario)
        case ("PUT", "/auth/me/alerts"):
            usuario["avisos"] = ["email": corpo["email"] ?? false, "push": corpo["push"] ?? false]
            return responder(url, 200, usuario)
        case ("PUT", "/auth/me/sharing"):
            usuario["compartilhamento"] = ["mostraTudoAoParceiro": corpo["mostrarTudoAoParceiro"] ?? false,
                                           "compartilharPorPadrao": corpo["compartilharPorPadrao"] ?? false]
            return responder(url, 200, usuario)
        case ("PUT", "/auth/me/modules"):
            var preferencias = usuario["preferencias"] as? [String: Any] ?? [:]
            preferencias["modulos"] = corpo["modulos"]
            usuario["preferencias"] = preferencias
            return responder(url, 200, usuario)
        case ("PUT", "/auth/me/senha"):
            usuario["temSenha"] = true
            return corpo["senhaAtual"] as? String == "errada"
                ? responder(url, 422, erro("A senha atual não confere.", codigo: "SENHA_ATUAL"))
                : responder(url, 200, usuario)
        case ("PUT", "/me/foto"):
            usuario["foto"] = "data:image/jpeg;base64," + (corpo["imagemBase64"] as? String ?? "")
            usuario["fotoPropria"] = true
            return responder(url, 200, usuario)
        case ("DELETE", "/me/foto"):
            usuario["foto"] = NSNull()
            usuario["fotoPropria"] = false
            return responder(url, 200, usuario)
        case ("DELETE", "/me"):
            logado = false
            return responder(url, 204, nil)
        case ("POST", "/auth/refresh"):
            // Como o backend: quem renovou pelo corpo recebe o novo no corpo.
            return logado
                ? responder(url, 200, tokens(noCorpo: corpo["refreshToken"] is String))
                : responder(url, 401, erro("Sua sessao venceu."))
        case ("POST", "/auth/logout"):
            logado = false
            return responder(url, 204, nil)
        case ("GET", "/auth/me"):
            return responder(url, 200, usuario)
        case ("GET", "/billing/plan"):
            return responder(url, 200, plano())
        case ("POST", "/billing/app-store/transactions"):
            // Sem a Apple de verdade: a compra no simulador (o .storekit) vale como assinatura.
            usuario["plano"] = "PREMIUM"
            usuario["premiumAte"] = Formatos.dia(Calendar.current.date(byAdding: .month, value: 1, to: Date()) ?? Date())
            origemDoPremium = "APP_STORE"
            return responder(url, 200, plano())
        case ("GET", "/categories"):
            return responder(url, 200, ["categorias": dados.categorias.sorted()])
        case ("POST", "/categories"):
            dados.categorias.append(corpo["nome"] as? String ?? "")
            return responder(url, 201, ["categorias": dados.categorias.sorted()])
        case ("GET", "/expenses"):
            let mes = consulta(url, "mes")
            let visao = consulta(url, "visao")
            let eu = usuario["id"] as? String
            let doMes = gastos.filter { gasto in
                (mes == nil || (gasto["data"] as? String)?.hasPrefix(mes!) == true)
                    && (visao != "meu" || gasto["usuario"] as? String == eu)
                    && (visao != "casal" || gasto["compartilhado"] as? Bool == true)
            }
            return responder(url, 200, doMes.sorted { ($0["data"] as! String) > ($1["data"] as! String) })
        case ("GET", "/expenses/search"):
            let termo = (consulta(url, "q") ?? "").lowercased()
            let achados = gastos.filter {
                "\($0["item"] ?? "") \($0["estabelecimento"] ?? "")".lowercased().contains(termo)
            }
            return responder(url, 200, Array(achados.prefix(20)))
        case ("POST", "/expenses"):
            return responder(url, 201, lancar(pedido.httpBody))
        case ("POST", "/expenses/import/preview"):
            // Qualquer arquivo vira o mesmo extrato; a ultima linha ja "entrou" antes.
            let hoje = Formatos.dia(Date())
            return responder(url, 200, [
                ["data": hoje, "descricao": "PIX ENVIADO PADARIA", "valor": 18.5, "idExterno": "demo:1",
                 "categoria": "Alimentação", "jaImportada": false],
                ["data": hoje, "descricao": "COMPRA CARTAO POSTO", "valor": 150, "idExterno": "demo:2",
                 "categoria": "Transporte", "jaImportada": false],
                ["data": hoje, "descricao": "DEBITO AUT ENEL", "valor": 189.32, "idExterno": "demo:3",
                 "categoria": "Casa", "jaImportada": true],
            ])
        case ("POST", "/expenses/import"):
            let linhas = corpo["linhas"] as? [[String: Any]] ?? []
            gastos += linhas.map {
                Self.gasto($0["descricao"] as? String ?? "", $0["categoria"] as? String ?? "Outros",
                           ($0["valor"] as? NSNumber)?.doubleValue ?? 0, $0["data"] as? String ?? "", "",
                           conta: corpo["contaId"] as? String)
            }
            return responder(url, 200, ["importados": linhas.count, "pulados": 0])
        case ("GET", "/reports/month"):
            return responder(url, 200, resumo(url))
        default:
            let partes = caminho.split(separator: "/").map(String.init)
            if metodo == "PUT", partes.count == 2, partes[0] == "expenses",
               let indice = gastos.firstIndex(where: { $0["id"] as? String == partes[1] }) {
                for chave in ["item", "categoria", "valor", "compartilhado"] where corpo[chave] != nil {
                    gastos[indice][chave] = corpo[chave]
                }
                return responder(url, 200, gastos[indice])
            }
            if metodo == "DELETE", partes.count == 2, partes[0] == "expenses" {
                gastos.removeAll { $0["id"] as? String == partes[1] }
                return responder(url, 204, nil)
            }
            if let (status, resposta) = dados.responder(metodo, partes, consulta: { self.consulta(url, $0) }, corpo: corpo,
                                                        gastos: gastos, eu: usuario["id"] as? String ?? "") {
                if let novo = resposta as? [String: Any], caminho == "/auth/me/preferences" {
                    usuario["preferencias"] = novo
                    return responder(url, status, usuario)
                }
                return responder(url, status, resposta)
            }
            return responder(url, 404, erro("Nao existe na demonstracao."))
        }
    }

    private func consulta(_ url: URL, _ nome: String) -> String? {
        URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == nome }?.value
    }

    private func tokens(noCorpo: Bool) -> [String: Any] {
        var tokens: [String: Any] = ["accessToken": "demo." + UUID().uuidString, "expiresIn": 1800, "usuario": usuario]
        if noCorpo {
            tokens["refreshToken"] = "demo-refresh." + UUID().uuidString
        }
        return tokens
    }

    /// O PlanoResponse do identity para a conta da demonstracao.
    private func plano() -> [String: Any] {
        let premium = usuario["plano"] as? String == "PREMIUM"
        let titular = ["TITULAR", "ADMIN"].contains(usuario["role"] as? String)
        return [
            "plano": premium ? "PREMIUM" : "GRATUITO",
            "premiumAte": premium ? usuario["premiumAte"] ?? NSNull() : NSNull(),
            "origem": premium ? origemDoPremium : NSNull(),
            "diasDeTeste": 7,
            "app": ["mensal": "22,89", "anual": "228,85"],
            "gratuito": ["Gastos, receitas, categorias, meta do mês e calendário",
                         "Leitura de nota fiscal pela foto: 10 por mês por família",
                         "Relatório do mês, comparação com o mês anterior e projeção do fim do mês",
                         "Cofrinho com depósitos da família e um cofrinho por objetivo",
                         "Lista de compras, lugares e filmes", "Prêmios e Fofocoins"],
            "premium": ["Tudo do gratuito", "Orçamento por categoria, com alerta em 80% e 100%",
                        "Relatório do ano com gráficos", "Leitura de nota fiscal pela foto sem limite",
                        "Mural de fotos", "Vários cofrinhos por objetivo, cada um com prazo e quanto guardar por mês",
                        "Assistente de IA que responde sobre os seus gastos"],
            "planoFamilia": false,
            "planoCasal": false,
            "voceAssinou": premium && titular,
            "casaTemAssinatura": premium,
        ]
    }

    private func erro(_ mensagem: String, codigo: String = "DEMO") -> [String: Any] {
        ["erro": mensagem, "codigo": codigo, "campos": [], "momento": ISO8601DateFormatter().string(from: Date())]
    }

    private func lancar(_ corpo: Data?) -> [[String: Any]] {
        guard let corpo,
              let json = try? JSONSerialization.jsonObject(with: corpo) as? [String: Any],
              let data = json["data"] as? String,
              let itens = json["itens"] as? [[String: Any]] else {
            return []
        }
        let novos = itens.map { item in
            Self.gasto(
                item["item"] as? String ?? "",
                item["categoria"] as? String ?? "Outros",
                (item["valor"] as? NSNumber)?.doubleValue ?? 0,
                data,
                json["estabelecimento"] as? String ?? "",
                conta: json["contaId"] as? String,
                compartilhado: json["compartilhado"] as? Bool ?? false,
                parcela: (json["parcelas"] as? Int).map { (1, $0) })
        }
        gastos.append(contentsOf: novos)
        return novos
    }

    private func resumo(_ url: URL) -> [String: Any] {
        let mes = URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?.first { $0.name == "mes" }?.value ?? Formatos.mes(Date())
        let doMes = gastos.filter { ($0["data"] as? String)?.hasPrefix(mes) == true }
        var porCategoria: [String: Double] = [:]
        for gasto in doMes {
            porCategoria[gasto["categoria"] as! String, default: 0] += gasto["valor"] as! Double
        }
        let total = porCategoria.values.reduce(0, +)
        let receitas = dados.receitas.filter { ($0["data"] as? String)?.hasPrefix(mes) == true }
            .reduce(0.0) { $0 + ($1["valor"] as? Double ?? 0) }
        let anterior = total > 0 ? 1480.0 : 0
        return [
            "mes": mes,
            "receitas": receitas,
            "gastos": total,
            "sobra": receitas - total,
            "taxaDePoupanca": receitas > 0 ? ((receitas - total) / receitas * 1000).rounded() / 10 : NSNull(),
            "gastosMesAnterior": anterior,
            "variacao": anterior > 0 ? ((total - anterior) / anterior * 1000).rounded() / 10 : NSNull(),
            "projecaoDeGastos": mes == Formatos.mes(Date()) ? total * 1.6 : NSNull(),
            "porCategoria": porCategoria
                .sorted { $0.value > $1.value }
                .map { ["categoria": $0.key, "total": $0.value, "anterior": $0.value * 0.9] },
        ]
    }

    private func responder(_ url: URL, _ status: Int, _ corpo: Any?) -> (Data, HTTPURLResponse) {
        // Data ja pronta e texto (o CSV da exportacao); o resto vira JSON.
        let texto = corpo as? Data
        let dados = texto ?? corpo.flatMap { try? JSONSerialization.data(withJSONObject: $0) } ?? Data()
        let resposta = HTTPURLResponse(
            url: url, statusCode: status, httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": texto == nil ? "application/json" : "text/csv"])!
        return (dados, resposta)
    }
}
#endif
