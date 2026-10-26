#if DEBUG
import Foundation

/// Responde como a API, sem rede, com dados inventados. So existe em Debug: serve para
/// o preview do SwiftUI e para ver as telas no simulador com `-PigguDemo YES`.
/// Qualquer e-mail e senha entram; `-PigguDemoLogado YES` ja abre logado.
///
/// `-PigguDemoFalha rede|servidor|vazio` simula, fora do login, a falta de internet, o erro
/// 500 e o mes sem nenhum gasto, para ver os estados das telas.
actor TransporteDeDemonstracao: Transporte {
    enum Falha: String {
        case rede, servidor, vazio
    }

    private var logado: Bool
    private var gastos: [[String: Any]]
    private let falha: Falha?

    init(logado: Bool = false, falha: Falha? = UserDefaults.standard.string(forKey: "PigguDemoFalha").flatMap(Falha.init)) {
        self.logado = logado
        self.falha = falha
        let hoje = Date()
        func dia(_ antes: Int) -> String {
            Formatos.dia(Calendar.current.date(byAdding: .day, value: -antes, to: hoje) ?? hoje)
        }
        // Sempre dentro do mes corrente, para o painel nao ficar vazio no dia 1.
        let diaDoMes = Calendar.current.component(.day, from: hoje)
        func noMes(_ antes: Int) -> String { dia(min(antes, diaDoMes - 1)) }

        gastos = [
            Self.gasto("Mercado da semana", "Mercado", 312.47, noMes(0), "Pão de Açúcar"),
            Self.gasto("Uber para o trabalho", "Transporte", 23.90, noMes(1), "Uber"),
            Self.gasto("Almoço", "Alimentação", 42.00, noMes(1), "Restaurante da esquina"),
            Self.gasto("Conta de luz", "Casa", 189.32, noMes(3), "Enel"),
            Self.gasto("Cinema", "Lazer", 64.00, noMes(5), "Cinemark"),
            Self.gasto("Farmácia", "Saúde", 37.80, noMes(6), "Drogasil"),
            Self.gasto("Gasolina", "Transporte", 220.00, noMes(8), "Posto Shell"),
        ]
    }

    private static func gasto(
        _ item: String, _ categoria: String, _ valor: Double, _ data: String, _ estabelecimento: String
    ) -> [String: Any] {
        [
            "id": UUID().uuidString.lowercased(),
            "data": data,
            "estabelecimento": estabelecimento,
            "item": item,
            "categoria": categoria,
            "valor": valor,
            "tipo": "Variavel",
            "origem": "manual",
            "usuario": Self.usuario["id"] as Any,
            "registradoEm": ISO8601DateFormatter().string(from: Date()),
            "compartilhado": false,
        ]
    }

    nonisolated(unsafe) private static let usuario: [String: Any] = [
        "id": "8a5c2f0e-0000-4000-8000-000000000001",
        "email": "demo@piggu.app",
        "nome": "Pessoa de Demonstração",
        "primeiroNome": "Pessoa",
        "apelido": NSNull(),
        "foto": NSNull(),
        "role": "TITULAR",
        "ativo": true,
        "plano": "GRATUITO",
        "premiumAte": NSNull(),
        "familia": "8a5c2f0e-0000-4000-8000-0000000000f1",
        "emailConfirmado": true,
        "pais": "BR",
        "nascimento": "1990-01-01",
    ]

    func enviar(_ pedido: URLRequest) async throws -> (Data, HTTPURLResponse) {
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
            case nil:
                break
            }
        }

        switch (metodo, caminho) {
        case ("POST", "/auth/entrar"):
            logado = true
            return responder(url, 200, tokens(noCorpo: corpo["refreshNoCorpo"] as? Bool == true))
        case ("POST", "/auth/refresh"):
            // Como o backend: quem renovou pelo corpo recebe o novo no corpo.
            return logado
                ? responder(url, 200, tokens(noCorpo: corpo["refreshToken"] is String))
                : responder(url, 401, erro("Sua sessao venceu."))
        case ("POST", "/auth/logout"):
            logado = false
            return responder(url, 204, nil)
        case ("GET", "/auth/me"):
            return responder(url, 200, Self.usuario)
        case ("GET", "/categories"):
            return responder(url, 200, ["categorias": [
                "Alimentação", "Casa", "Educação", "Lazer", "Mercado", "Outros", "Saúde", "Transporte",
            ]])
        case ("GET", "/expenses"):
            let mes = URLComponents(url: url, resolvingAgainstBaseURL: false)?
                .queryItems?.first { $0.name == "mes" }?.value
            let doMes = gastos.filter { mes == nil || ($0["data"] as? String)?.hasPrefix(mes!) == true }
            return responder(url, 200, doMes.sorted { ($0["data"] as! String) > ($1["data"] as! String) })
        case ("POST", "/expenses"):
            return responder(url, 201, lancar(pedido.httpBody))
        case ("GET", "/reports/month"):
            return responder(url, 200, resumo(url))
        default:
            return responder(url, 404, erro("Nao existe na demonstracao."))
        }
    }

    private func tokens(noCorpo: Bool) -> [String: Any] {
        var tokens: [String: Any] = ["accessToken": "demo." + UUID().uuidString, "expiresIn": 1800, "usuario": Self.usuario]
        if noCorpo {
            tokens["refreshToken"] = "demo-refresh." + UUID().uuidString
        }
        return tokens
    }

    private func erro(_ mensagem: String) -> [String: Any] {
        ["erro": mensagem, "codigo": "DEMO", "campos": [], "momento": ISO8601DateFormatter().string(from: Date())]
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
                json["estabelecimento"] as? String ?? "")
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
        let receitas = total > 0 ? 5200.0 : 0
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
        let dados = corpo.flatMap { try? JSONSerialization.data(withJSONObject: $0) } ?? Data()
        let resposta = HTTPURLResponse(
            url: url, statusCode: status, httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"])!
        return (dados, resposta)
    }
}
#endif
