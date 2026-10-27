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
    var contasDoMes: [[String: Any]] = [
        ["id": "cf-aluguel", "descricao": "Aluguel", "categoria": "Casa", "valor": 1800.0, "dia": 5, "automatico": true],
        ["id": "cf-luz", "descricao": "Conta de luz", "categoria": "Casa", "valor": 189.32, "dia": 12, "automatico": false],
        ["id": "cf-internet", "descricao": "Internet", "categoria": "Casa", "valor": 119.9, "dia": 28, "automatico": false],
        ["id": "cf-academia", "descricao": "Academia", "categoria": "Saúde", "valor": 99.0, "dia": 30, "automatico": true],
    ]
    /// "id/aaaa-mm" das contas do mes ja pagas.
    var contasPagas: Set<String> = []
    var orcamentos: [[String: Any]] = [
        ["id": "o-mercado", "categoria": "Mercado", "limite": 380.0],
        ["id": "o-lazer", "categoria": "Lazer", "limite": 250.0],
    ]
    var contas: [[String: Any]] = [
        ["id": cartao, "nome": "Nubank roxinho", "tipo": "CARTAO", "fechamento": 3, "vencimento": 10, "compartilhado": false],
        ["id": banco, "nome": "Conta do Itaú", "tipo": "CONTA", "compartilhado": true],
    ]
    /// "id/aaaa-mm" das faturas marcadas como pagas.
    var faturasPagas: Set<String> = []
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
        contasDoMes = []
        orcamentos = []
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

    static func somar(_ meses: Int, a mes: String) -> String {
        let partes = mes.split(separator: "-").compactMap { Int($0) }
        let total = partes[0] * 12 + partes[1] - 1 + meses
        return String(format: "%04d-%02d", total / 12, total % 12 + 1)
    }

    private func contaDoMes(_ conta: [String: Any], mes: String) -> [String: Any] {
        let dia = min(conta["dia"] as? Int ?? 1, 28)
        let vencimento = String(format: "%@-%02d", mes, dia)
        let paga = contasPagas.contains("\(conta["id"] ?? "")/\(mes)")
        var linha = conta
        linha["vencimento"] = vencimento
        linha["situacao"] = paga ? "PAGA" : vencimento < Formatos.dia(Date()) ? "VENCIDA" : "PENDENTE"
        linha["tipo"] = "GASTO"
        return linha
    }

    private func mesDaFatura(_ conta: [String: Any], compra: String) -> String? {
        guard let fechamento = conta["fechamento"] as? Int, let vencimento = conta["vencimento"] as? Int else { return nil }
        return FaturaDoCartao.daCompra(fechamento: fechamento, vencimento: vencimento, compra: compra)?.mes
    }

    private func fatura(_ conta: [String: Any], mes: String, gastos: [[String: Any]]) -> [String: Any] {
        let fechamento = conta["fechamento"] as? Int ?? 1
        let vencimento = conta["vencimento"] as? Int ?? 10
        let total = gastos.filter { $0["contaId"] as? String == conta["id"] as? String
            && ($0["data"] as? String).flatMap { mesDaFatura(conta, compra: $0) } == mes }
            .reduce(0.0) { $0 + ($1["valor"] as? Double ?? 0) }
        let id = conta["id"] as? String ?? ""
        let paga = faturasPagas.contains { $0.hasPrefix(id + "/") && String($0.dropFirst(id.count + 1)) >= mes }
        return [
            "mes": mes,
            "inicio": String(format: "%@-%02d", Self.somar(-1, a: mes), fechamento),
            "ultimaCompra": String(format: "%@-%02d", mes, max(fechamento - 1, 1)),
            "fechamento": String(format: "%@-%02d", mes, fechamento),
            "vencimento": String(format: "%@-%02d", vencimento > fechamento ? mes : Self.somar(1, a: mes), vencimento),
            "total": total, "paga": paga,
        ]
    }

    private func relatorioDoAno(_ ano: Int, gastos: [[String: Any]]) -> [String: Any] {
        let hoje = Formatos.mes(Date())
        var porCategoria: [String: Double] = [:]
        let meses: [[String: Any]] = (1...12).map { numero in
            let mes = String(format: "%04d-%02d", ano, numero)
            var receitasDoMes = 0.0
            var gastosDoMes = 0.0
            if mes == hoje {
                receitasDoMes = receitas.filter { ($0["data"] as? String)?.hasPrefix(mes) == true }
                    .reduce(0.0) { $0 + ($1["valor"] as? Double ?? 0) }
                for gasto in gastos where (gasto["data"] as? String)?.hasPrefix(mes) == true {
                    gastosDoMes += gasto["valor"] as? Double ?? 0
                    porCategoria[gasto["categoria"] as? String ?? "Outros", default: 0] += gasto["valor"] as? Double ?? 0
                }
            } else if mes < hoje {
                // Meses passados inventados, para o grafico do ano ter forma.
                receitasDoMes = 5200
                gastosDoMes = 3100 + Double((numero * 389) % 1300)
                for (categoria, parte) in [("Casa", 0.4), ("Mercado", 0.25), ("Transporte", 0.15), ("Lazer", 0.12), ("Saúde", 0.08)] {
                    porCategoria[categoria, default: 0] += gastosDoMes * parte
                }
            }
            return ["mes": mes, "receitas": receitasDoMes, "gastos": gastosDoMes, "sobra": receitasDoMes - gastosDoMes]
        }
        let receitas = meses.reduce(0.0) { $0 + ($1["receitas"] as? Double ?? 0) }
        let gastosDoAno = meses.reduce(0.0) { $0 + ($1["gastos"] as? Double ?? 0) }
        return [
            "ano": ano, "receitas": receitas, "gastos": gastosDoAno, "sobra": receitas - gastosDoAno, "meses": meses,
            "porCategoria": porCategoria.sorted { $0.value > $1.value }
                .map { ["categoria": $0.key, "total": ($0.value * 100).rounded() / 100] },
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
            return (200, contasDoMes.map { contaDoMes($0, mes: mes) })
        case ("POST", "bills"):
            var nova = corpo
            nova["id"] = Self.novoId()
            contasDoMes.append(nova)
            return (201, nil)
        case ("POST", _) where partes.count == 3 && partes[0] == "bills" && partes[2] == "pay":
            let chave = "\(partes[1])/\(mes)"
            guard !contasPagas.contains(chave) else {
                return (422, ["erro": "Esta conta já foi lançada neste mês.", "codigo": "JA_PAGA"])
            }
            contasPagas.insert(chave)
            return (201, ["id": Self.novoId(), "data": Formatos.dia(Date()), "item": "Conta", "categoria": "Casa",
                          "valor": 0, "usuario": eu])
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
            contasDoMes.removeAll { $0["id"] as? String == partes[1] }
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
            let valor = (corpo["valor"] as? NSNumber)?.doubleValue ?? 0
            let nota: [String: Any] = [
                "id": Self.novoId(), "titulo": corpo["titulo"] ?? "", "texto": corpo["texto"] ?? NSNull(),
                "data": corpo["data"] ?? NSNull(), "valor": valor, "categoria": corpo["categoria"] ?? NSNull(),
                "gastoId": valor > 0 ? Self.novoId() : NSNull(),
                "criadoEm": ISO8601DateFormatter().string(from: Date()),
            ]
            notas.append(nota)
            return (201, nota)
        case ("DELETE", _) where partes.count == 2 && partes[0] == "notes":
            return remover(&notas)
        case ("GET", "budgets"):
            return (200, orcamentos.map { orcamento in
                let categoria = orcamento["categoria"] as? String
                let gasto = gastos.filter { $0["categoria"] as? String == categoria && ($0["data"] as? String)?.hasPrefix(mes) == true }
                    .reduce(0.0) { $0 + ($1["valor"] as? Double ?? 0) }
                let limite = orcamento["limite"] as? Double ?? 1
                let percentual = (gasto / limite * 1000).rounded() / 10
                var linha = orcamento
                linha["gasto"] = gasto
                linha["percentual"] = percentual
                linha["alerta"] = percentual >= 100 ? "ESTOUROU" : percentual >= 80 ? "ATENCAO" : "OK"
                return linha
            })
        case ("PUT", "budgets"):
            orcamentos.removeAll { $0["categoria"] as? String == corpo["categoria"] as? String }
            orcamentos.append(["id": Self.novoId(), "categoria": corpo["categoria"] ?? "",
                               "limite": (corpo["limite"] as? NSNumber)?.doubleValue ?? 0])
            return (204, nil)
        case ("DELETE", _) where partes.count == 2 && partes[0] == "budgets":
            return remover(&orcamentos)
        case ("PUT", "monthly-goals"):
            if let mes = corpo["mes"] as? String { limites[mes] = (corpo["limite"] as? NSNumber)?.doubleValue }
            return (200, limites)
        case ("GET", "reports/year"):
            return (200, relatorioDoAno(Int(consulta("ano") ?? "") ?? 2026, gastos: gastos))
        case ("GET", "expenses/export"):
            let linhas = gastos.filter { ($0["data"] as? String)?.hasPrefix(mes) == true }.map { gasto in
                ["data", "item", "categoria", "estabelecimento"].map { "\(gasto[$0] ?? "")" }.joined(separator: ";")
                    + ";" + String(format: "%.2f", gasto["valor"] as? Double ?? 0).replacingOccurrences(of: ".", with: ",")
            }
            return (200, Data((["data;item;categoria;estabelecimento;valor"] + linhas).joined(separator: "\n").utf8))
        case ("GET", "accounts"):
            return (200, contas.map { conta in
                var linha = conta
                linha["usuario"] = eu
                if let aberta = mesDaFatura(conta, compra: Formatos.dia(Date())) {
                    linha["faturaAberta"] = fatura(conta, mes: aberta, gastos: gastos)
                    linha["faturaAPagar"] = fatura(conta, mes: Self.somar(-1, a: aberta), gastos: gastos)
                }
                return linha
            })
        case ("POST", "accounts"):
            var nova = corpo
            nova["id"] = Self.novoId()
            contas.append(nova)
            return (201, nova)
        case ("PUT", _) where partes.count == 3 && partes[0] == "accounts" && partes[2] == "sharing":
            if let indice = contas.firstIndex(where: { $0["id"] as? String == partes[1] }) {
                contas[indice]["compartilhado"] = corpo["compartilhado"]
            }
            return (204, nil)
        case ("DELETE", _) where partes.count == 2 && partes[0] == "accounts":
            return remover(&contas)
        case ("GET", _) where partes.count == 3 && partes[0] == "accounts" && partes[2] == "statement":
            guard let conta = contas.first(where: { $0["id"] as? String == partes[1] }) else { return (404, nil) }
            let doMes = gastos.filter { $0["contaId"] as? String == partes[1]
                && ($0["data"] as? String).flatMap { mesDaFatura(conta, compra: $0) } == mes }
            return (200, ["fatura": fatura(conta, mes: mes, gastos: gastos), "gastos": doMes])
        case ("PUT", _) where partes.count == 4 && partes[2] == "statement":
            faturasPagas.insert("\(partes[1])/\(mes)")
            return (204, nil)
        case ("DELETE", _) where partes.count == 4 && partes[2] == "statement":
            faturasPagas = faturasPagas.filter { !$0.hasPrefix(partes[1]) || $0 < "\(partes[1])/\(mes)" }
            return (204, nil)
        case ("POST", "banking/connect-token"):
            return (201, ["accessToken": "demo-connect", "sandbox": true])
        case ("POST", "banking/items"):
            contasBancarias.append(["id": Self.novoId(), "conexaoId": "cx2", "instituicao": "Banco do Brasil",
                                    "nome": "Conta corrente", "tipo": "BANK", "numero": "1234-5", "saldo": 880.0,
                                    "moeda": "BRL", "status": "UPDATED"])
            return (201, contasBancarias)
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
