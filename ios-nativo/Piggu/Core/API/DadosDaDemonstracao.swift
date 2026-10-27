#if DEBUG
import Foundation

/// O resto da API de demonstracao (receitas, cofrinho, contas, familia...), separado do
/// TransporteDeDemonstracao para ele continuar so com a entrada e os gastos. Responde com
/// dicionarios no formato do backend; nil quando a rota nao e daqui.
struct DadosDaDemonstracao {
    static let cartao = "c0000000-0000-4000-8000-00000000ca01"
    static let banco = "c0000000-0000-4000-8000-00000000ba01"
    static let parceira = "8a5c2f0e-0000-4000-8000-000000000002"

    var categorias = ["Alimentação", "Casa", "Educação", "Lazer", "Mercado", "Outros", "Saúde", "Transporte"]
    var receitas: [[String: Any]]
    var aReceber: [[String: Any]]
    var recorrentes: [[String: Any]]
    var depositos: [[String: Any]]
    var objetivos: [[String: Any]]
    var notas: [[String: Any]]
    var regras: [[String: Any]] = [["id": novoId(), "termo": "Uber", "categoria": "Transporte"]]
    var limites: [String: Double]
    var contasBancarias: [[String: Any]] = [
        ["id": novoId(), "conexaoId": "cx1", "instituicao": "Nubank", "nome": "Conta corrente", "tipo": "BANK",
         "numero": "0001-9", "saldo": 4210.55, "moeda": "BRL", "status": "UPDATED"],
        ["id": novoId(), "conexaoId": "cx1", "instituicao": "Nubank", "nome": "Caixinha", "tipo": "BANK",
         "numero": "", "saldo": 1500.0, "moeda": "BRL", "status": "UPDATED"],
    ]

    init() {
        let hoje = Date()
        let mes = Formatos.mes(hoje)
        func dia(_ antes: Int) -> String {
            Formatos.dia(Calendar.current.date(byAdding: .day, value: -antes, to: hoje) ?? hoje)
        }
        let diaDoMes = Calendar.current.component(.day, from: hoje)
        func noMes(_ antes: Int) -> String { dia(min(antes, diaDoMes - 1)) }
        receitas = [
            ["id": Self.novoId(), "data": "\(mes)-01", "descricao": "Salário", "categoria": "Salário",
             "valor": 5200.0, "usuario": TransporteDeDemonstracao.idDaPessoa, "compartilhado": false],
            ["id": Self.novoId(), "data": noMes(4), "descricao": "Freela do site", "categoria": "Freelance",
             "valor": 850.0, "usuario": TransporteDeDemonstracao.idDaPessoa, "compartilhado": false],
            ["id": Self.novoId(), "data": noMes(2), "descricao": "Aluguel do quarto", "categoria": "Aluguel",
             "valor": 900.0, "usuario": Self.parceira, "compartilhado": true],
        ]
        aReceber = [
            ["id": Self.novoId(), "quem": "João", "descricao": "Metade da pizza", "valor": 45.0, "data": dia(3)],
            ["id": Self.novoId(), "quem": "Ana", "descricao": "Ingresso do show", "valor": 180.0, "data": dia(-10)],
        ]
        recorrentes = [
            ["id": Self.novoId(), "descricao": "Salário", "categoria": "Salário", "valor": 5200.0, "dia": 1,
             "automatico": true, "vencimento": "\(mes)-01", "situacao": "PAGA", "tipo": "RECEITA"],
        ]
        depositos = [
            ["id": Self.novoId(), "data": dia(2), "valor": 200.0, "usuario": TransporteDeDemonstracao.idDaPessoa],
            ["id": Self.novoId(), "data": dia(16), "valor": 350.0, "usuario": Self.parceira],
        ]
        objetivos = [
            Self.objetivo(nome: "Viagem para Floripa", alvo: 3000, guardado: 1150,
                          prazo: Formatos.dia(Calendar.current.date(byAdding: .month, value: 6, to: hoje)!)),
        ]
        notas = [
            ["id": Self.novoId(), "titulo": "Renovar o seguro do carro", "texto": "Pedir 3 cotações",
             "data": NSNull(), "criadoEm": ISO8601DateFormatter().string(from: hoje)],
        ]
        limites = [mes: 2500]
    }

    static func novoId() -> String { UUID().uuidString.lowercased() }

    mutating func esvaziar() {
        receitas = []
        aReceber = []
        recorrentes = []
        depositos = []
        objetivos = []
        notas = []
        regras = []
        limites = [:]
    }

    private static func objetivo(nome: String, alvo: Double, guardado: Double, prazo: String?,
                                 id: String = novoId()) -> [String: Any] {
        let falta = max(alvo - guardado, 0)
        let meses = prazo.flatMap { Formatos.data(deDia: $0) }
            .map { max(Calendar.current.dateComponents([.month], from: Date(), to: $0).month ?? 0, 1) }
        return [
            "id": id, "nome": nome, "alvo": alvo, "prazo": prazo as Any? ?? NSNull(), "guardado": guardado,
            "falta": falta, "percentual": min(Int(guardado / alvo * 100), 100),
            "mesesRestantes": meses as Any? ?? NSNull(),
            "porMes": meses.map { (falta / Double($0) * 100).rounded() / 100 } as Any? ?? NSNull(),
            "concluido": falta == 0, "atrasado": false, "usuario": TransporteDeDemonstracao.idDaPessoa,
            "compartilhado": false,
        ]
    }

    private var cofrinho: [String: Any] {
        let total = depositos.reduce(0.0) { $0 + ($1["valor"] as? Double ?? 0) }
        return ["depositos": depositos, "totalDepositos": total, "totalGastos": 420.0, "saldo": total - 420]
    }

    // swiftlint:disable:next cyclomatic_complexity function_body_length
    mutating func responder(
        _ metodo: String, _ partes: [String], consulta: (String) -> String?, corpo: [String: Any],
        gastos: [[String: Any]], eu: String
    ) -> (Int, Any?)? {
        let mes = consulta("mes") ?? Formatos.mes(Date())
        func remover(_ lista: inout [[String: Any]]) -> (Int, Any?) {
            lista.removeAll { $0["id"] as? String == partes.last }
            return (204, nil)
        }
        switch (metodo, partes.joined(separator: "/")) {
        case ("GET", "incomes"):
            return (200, receitas.filter { ($0["data"] as? String)?.hasPrefix(mes) == true })
        case ("GET", "incomes/categories"):
            return (200, ["Salário", "Freelance", "Aluguel", "Investimentos", "Presente", "Outros"])
        case ("POST", "incomes"):
            var nova = corpo
            nova["id"] = Self.novoId()
            nova["usuario"] = eu
            receitas.append(nova)
            if corpo["recorrente"] as? Bool == true, let data = corpo["data"] as? String {
                recorrentes.append(["id": Self.novoId(), "descricao": corpo["descricao"] ?? "",
                                    "categoria": corpo["categoria"] ?? "", "valor": corpo["valor"] ?? 0,
                                    "dia": Int(data.suffix(2)) ?? 1, "automatico": true, "vencimento": data,
                                    "situacao": "PAGA", "tipo": "RECEITA"])
            }
            return (201, nova)
        case ("GET", "bills") where consulta("tipo") == "RECEITA":
            return (200, recorrentes)
        case ("GET", "bills"):
            let conta: [String: Any] = [
                "id": Self.novoId(), "descricao": "Internet", "categoria": "Casa", "valor": 119.9, "dia": 28,
                "automatico": false, "vencimento": "\(mes)-28", "situacao": "PENDENTE", "tipo": "GASTO",
            ]
            return (200, receitas.isEmpty ? [] : [conta])
        case ("GET", "incomes/receivables"):
            return (200, aReceber)
        case ("POST", "incomes/receivables"):
            var nova = corpo
            nova["id"] = Self.novoId()
            aReceber.append(nova)
            return (201, nova)
        case ("POST", _) where partes.count == 4 && partes[1] == "receivables" && partes[3] == "receive":
            guard let conta = aReceber.first(where: { $0["id"] as? String == partes[2] }) else { return (404, nil) }
            aReceber.removeAll { $0["id"] as? String == partes[2] }
            let receita: [String: Any] = [
                "id": Self.novoId(), "data": Formatos.dia(Date()),
                "descricao": "\(conta["quem"] ?? "") pagou", "categoria": "Outros", "valor": conta["valor"] ?? 0,
                "usuario": eu, "compartilhado": false,
            ]
            receitas.append(receita)
            return (200, receita)
        case ("DELETE", _) where partes.count == 3 && partes[1] == "receivables":
            return remover(&aReceber)
        case ("DELETE", _) where partes.count == 2 && partes[0] == "incomes":
            return remover(&receitas)
        case ("DELETE", _) where partes.count == 2 && partes[0] == "bills":
            return remover(&recorrentes)
        case ("GET", "monthly-goals"):
            return (200, limites)
        case ("GET", "piggy-bank"):
            return (200, cofrinho)
        case ("POST", "piggy-bank/deposits"):
            let deposito: [String: Any] = [
                "id": Self.novoId(), "data": corpo["data"] ?? Formatos.dia(Date()), "valor": corpo["valor"] ?? 0,
                "usuario": eu, "objetivo": corpo["objetivo"] ?? NSNull(),
            ]
            if let objetivo = corpo["objetivo"] as? String,
               let indice = objetivos.firstIndex(where: { $0["id"] as? String == objetivo }) {
                let atual = objetivos[indice]
                objetivos[indice] = Self.objetivo(
                    nome: atual["nome"] as? String ?? "", alvo: atual["alvo"] as? Double ?? 0,
                    guardado: (atual["guardado"] as? Double ?? 0) + ((corpo["valor"] as? NSNumber)?.doubleValue ?? 0),
                    prazo: atual["prazo"] as? String, id: objetivo)
            } else {
                depositos.insert(deposito, at: 0)
            }
            return (201, deposito)
        case ("DELETE", _) where partes.count == 3 && partes[1] == "deposits":
            return remover(&depositos)
        case ("GET", "piggy-bank/goals"):
            return (200, objetivos)
        case ("POST", "piggy-bank/goals"):
            let novo = Self.objetivo(nome: corpo["nome"] as? String ?? "",
                                     alvo: (corpo["alvo"] as? NSNumber)?.doubleValue ?? 0, guardado: 0,
                                     prazo: corpo["prazo"] as? String)
            objetivos.append(novo)
            return (201, novo)
        case ("PUT", _) where partes.count == 3 && partes[1] == "goals":
            guard let indice = objetivos.firstIndex(where: { $0["id"] as? String == partes[2] }) else {
                return (404, nil)
            }
            objetivos[indice] = Self.objetivo(
                nome: corpo["nome"] as? String ?? "", alvo: (corpo["alvo"] as? NSNumber)?.doubleValue ?? 0,
                guardado: objetivos[indice]["guardado"] as? Double ?? 0, prazo: corpo["prazo"] as? String,
                id: partes[2])
            return (204, nil)
        case ("DELETE", _) where partes.count == 3 && partes[1] == "goals":
            return remover(&objetivos)
        case ("GET", "notes"):
            return (200, notas)
        case ("POST", "notes"):
            let nota: [String: Any] = [
                "id": Self.novoId(), "titulo": corpo["titulo"] ?? "", "texto": corpo["texto"] ?? NSNull(),
                "data": NSNull(), "criadoEm": ISO8601DateFormatter().string(from: Date()),
            ]
            notas.append(nota)
            return (201, nota)
        case ("DELETE", _) where partes.count == 2 && partes[0] == "notes":
            return remover(&notas)
        case ("GET", "budgets"):
            let mercado = gastos.filter { $0["categoria"] as? String == "Mercado" && ($0["data"] as? String)?.hasPrefix(mes) == true }
                .reduce(0.0) { $0 + ($1["valor"] as? Double ?? 0) }
            guard mercado > 0 else { return (200, [Any]()) }
            return (200, [["id": "o1", "categoria": "Mercado", "limite": 380.0, "gasto": mercado,
                           "percentual": (mercado / 380 * 100).rounded(), "alerta": mercado > 380 ? "ESTOUROU" : mercado > 304 ? "ATENCAO" : "OK"]])
        case ("GET", "accounts"):
            let vencimento = Formatos.dia(Calendar.current.date(byAdding: .day, value: 5, to: Date())!)
            return (200, [
                ["id": Self.cartao, "nome": "Nubank roxinho", "tipo": "CARTAO", "fechamento": 3, "vencimento": 10,
                 "faturaAPagar": ["mes": mes, "vencimento": vencimento, "total": 1234.56], "usuario": eu],
                ["id": Self.banco, "nome": "Conta do Itaú", "tipo": "CONTA", "fechamento": NSNull(),
                 "vencimento": NSNull(), "faturaAPagar": NSNull(), "usuario": eu],
            ])
        case ("GET", "exchange-rate"):
            let de = consulta("de") ?? "USD"
            let taxas = ["USD": 5.42, "EUR": 6.12, "GBP": 7.05, "ARS": 0.0054, "BRL": 1.0]
            return (200, ["de": de, "para": consulta("para") ?? "BRL", "taxa": taxas[de] ?? 1.0,
                          "data": Formatos.dia(Date()), "fonte": "BCE", "estimativa": false, "desatualizada": false])
        case ("GET", "products/price-check"):
            return (200, ["media": 9.9, "compras": 4, "percentual": 23.0, "acima": true])
        case ("GET", "categories/rules"):
            return (200, regras)
        case ("PUT", "categories/rules"):
            let regra: [String: Any] = ["id": Self.novoId(), "termo": corpo["termo"] ?? "", "categoria": corpo["categoria"] ?? ""]
            regras.removeAll { ($0["termo"] as? String)?.lowercased() == (corpo["termo"] as? String)?.lowercased() }
            regras.append(regra)
            return (200, regra)
        case ("DELETE", _) where partes.count == 3 && partes[1] == "rules":
            return remover(&regras)
        case ("GET", "expenses/splits"):
            return (200, gastos.isEmpty ? [Any]() : [
                ["pessoa": eu, "pagou": 189.32, "parte": 126.66, "saldo": 62.66],
                ["pessoa": Self.parceira, "pagou": 64.0, "parte": 126.66, "saldo": -62.66],
            ])
        case ("GET", "expenses/splits/transfers"):
            return (200, [["de": Self.parceira, "para": eu, "valor": 62.66]])
        case ("GET", "personal-goals"):
            return (200, [
                ["id": "m1", "titulo": "Cozinhar em casa 4x", "alvo": 4, "atual": 4, "mes": mes, "concluida": true],
                ["id": "m2", "titulo": "Caminhar 10 km", "alvo": 10, "atual": 6, "mes": mes, "concluida": false],
                ["id": "m3", "titulo": "Ler um livro", "alvo": 1, "atual": 0, "mes": mes, "concluida": false],
            ])
        case ("GET", "girls-math"):
            return (200, [["id": "gm1", "produto": "Bolsa", "loja": "Arezzo", "valor": 389.9, "data": Formatos.dia(Date()),
                           "fotoAssetId": NSNull(), "temFoto": false]])
        case ("GET", "family"):
            return (200, ["id": "f1", "nome": "Casa", "plano": "GRATUITO", "planoFamilia": false, "convites": [Any](),
                          "membros": [
                              ["id": eu, "nome": "Pessoa", "email": "demo@piggu.app", "foto": NSNull(),
                               "papel": "TITULAR", "premium": false],
                              ["id": Self.parceira, "nome": "Bia", "email": "bia@piggu.app", "foto": NSNull(),
                               "papel": "PARCEIRO", "premium": false],
                          ]])
        case ("GET", "banking/status"):
            return (200, ["habilitado": true])
        case ("GET", "banking/accounts"), ("POST", "banking/sync"):
            return (200, contasBancarias)
        case ("DELETE", _) where partes.count == 3 && partes[1] == "connections":
            contasBancarias.removeAll { $0["conexaoId"] as? String == partes[2] }
            return (200, contasBancarias)
        case ("PUT", "auth/me/preferences"):
            return (200, corpo)
        default:
            return nil
        }
    }
}
#endif
