#if DEBUG
import Foundation
import UIKit

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
    var assistenteAutorizado = false
    var perguntasAoAssistente = 0
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
    var metasDoMes: [[String: Any]] = [
        ["id": "m1", "titulo": "Cozinhar em casa", "alvo": 4, "atual": 4],
        ["id": "m2", "titulo": "Caminhar 10 km", "alvo": 10, "atual": 6],
        ["id": "m3", "titulo": "Ler um livro", "alvo": 1, "atual": 0],
    ]
    var girlsMath: [[String: Any]] = [
        ["id": "gm1", "produto": "Bolsa de couro", "loja": "Arezzo", "valor": 389.9, "temFoto": false],
        ["id": "gm2", "produto": "Tênis de corrida", "loja": "Centauro", "valor": 499.0, "temFoto": false],
    ]
    var moedas = 120
    var movimentos: [[String: Any]] = [
        ["id": "mv1", "valor": 50, "motivo": "Arrumou a cozinha"],
        ["id": "mv2", "valor": 70, "motivo": "Lavou o carro"],
    ]
    var premios: [[String: Any]] = [
        ["id": "p1", "nome": "Escolher o filme de sexta", "descricao": "Sem reclamação", "preco": 60, "ativo": true],
        ["id": "p2", "nome": "Jantar fora", "descricao": "", "preco": 300, "ativo": true],
    ]
    var resgates: [[String: Any]] = []
    var convites: [[String: Any]] = [
        ["id": "cv1", "email": "joao@exemplo.com", "venceEm": "2026-11-03"],
    ]
    var nomeDaFamilia = "Casa da Pessoa"
    var compras: [[String: Any]] = [
        ["id": "c1", "item": "Ovos", "quantidade": "12", "lista": "Compras", "comprado": false, "marca": "", "imagem": "", "codigo": ""],
        ["id": "c2", "item": "Leite integral", "quantidade": "2 L", "lista": "Compras", "comprado": false, "marca": "Piracanjuba", "imagem": "", "codigo": "789"],
        ["id": "c3", "item": "Café", "quantidade": "500 g", "lista": "Compras", "comprado": true, "marca": "", "imagem": "", "codigo": ""],
        ["id": "c4", "item": "Air fryer", "quantidade": "", "lista": "Desejos", "comprado": false, "marca": "Mondial", "imagem": "", "codigo": ""],
    ]
    var lugares: [[String: Any]] = [
        ["id": "l1", "nome": "Coco Bambu", "categoria": "Restaurante", "localizacao": "Florianópolis, SC", "nota": 5,
         "comentario": "O camarão vale cada centavo.", "marcacoes": ["Aniversário"], "temFoto": false, "valor": 280.0],
        ["id": "l2", "nome": "Parque Ibirapuera", "categoria": "Passeio", "localizacao": "São Paulo, SP", "nota": 4,
         "comentario": "", "marcacoes": ["Domingo"], "temFoto": false, "valor": 0.0],
    ]
    var marcadores = ["Aniversário", "Domingo", "Viagem"]
    var filmes: [[String: Any]] = [
        ["id": "f1", "tmdbId": "550", "titulo": "Clube da Luta", "ano": "1999", "poster": "", "nota": 8.4,
         "sinopse": "", "assistido": false, "avaliacoes": [String: Int]()],
        ["id": "f2", "tmdbId": "13", "titulo": "Forrest Gump", "ano": "1994", "poster": "", "nota": 8.5,
         "sinopse": "", "assistido": true, "avaliacoes": [parceira: 5]],
    ]
    var feed: [[String: Any]] = [
        ["id": "ft1", "assetId": "demo-foto-1", "legenda": "Pôr do sol na praia"],
        ["id": "ft2", "assetId": "demo-foto-2", "legenda": ""],
    ]
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

    /// Uma "foto" em degrade para o feed e as miniaturas da demonstracao.
    static func imagemInventada(_ semente: String) -> Data {
        let tons: [(UIColor, UIColor)] = [(.systemPink, .systemOrange), (.systemTeal, .systemIndigo), (.systemPurple, .systemPink)]
        let (de, ate) = tons[abs(semente.hashValue) % tons.count]
        let formato = UIGraphicsImageRendererFormat()
        formato.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: 600, height: 600), format: formato).jpegData(withCompressionQuality: 0.8) { contexto in
            let degrade = CGGradient(colorsSpace: nil, colors: [de.cgColor, ate.cgColor] as CFArray, locations: [0, 1])!
            contexto.cgContext.drawLinearGradient(degrade, start: .zero, end: CGPoint(x: 600, y: 600), options: [])
        }
    }

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

    private func meta(of meta: [String: Any], mes: String) -> [String: Any] {
        var linha = meta
        linha["mes"] = mes
        linha["concluida"] = (meta["atual"] as? Int ?? 0) >= (meta["alvo"] as? Int ?? 1)
        return linha
    }

    private var saldo: [String: Any] {
        ["saldo": moedas, "historico": movimentos.enumerated().map { indice, movimento in
            var linha = movimento
            linha["data"] = Formatos.dia(Calendar.current.date(byAdding: .day, value: -indice * 2, to: Date())!)
            linha["usuario"] = "USUARIA"
            return linha
        }]
    }

    private func familia(_ eu: String) -> [String: Any] {
        ["id": "f1", "nome": nomeDaFamilia, "plano": "GRATUITO", "planoFamilia": false, "planoCasal": false,
         "convites": convites,
         "membros": [
             ["id": eu, "nome": "Pessoa de Demonstração", "email": "demo@piggu.app", "foto": NSNull(), "papel": "TITULAR",
              "premium": false],
             ["id": Self.parceira, "nome": "Bia", "email": "bia@piggu.app", "foto": NSNull(), "papel": "PARCEIRO",
              "premium": false],
             ["id": "8a5c2f0e-0000-4000-8000-000000000003", "nome": "Lucas", "email": "lucas@piggu.app", "foto": NSNull(),
              "papel": "MEMBRO", "premium": false],
         ]]
    }

    private var cofrinho: [String: Any] {
        let total = depositos.reduce(0.0) { $0 + ($1["valor"] as? Double ?? 0) }
        return ["depositos": depositos, "totalDepositos": total, "totalGastos": 420.0, "saldo": total - 420]
    }

    private func situacaoDoAssistente() -> [String: Any] {
        ["habilitado": true, "autorizado": assistenteAutorizado, "limite": 150, "restantes": 150 - perguntasAoAssistente]
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
            return (200, metasDoMes.map { meta(of: $0, mes: Formatos.mes(Date())) })
        case ("POST", "personal-goals"):
            var nova = corpo
            nova["id"] = Self.novoId()
            metasDoMes.append(nova)
            return (201, meta(of: nova, mes: Formatos.mes(Date())))
        case ("PUT", _) where partes.count == 2 && partes[0] == "personal-goals":
            guard let indice = metasDoMes.firstIndex(where: { $0["id"] as? String == partes[1] }) else { return (404, nil) }
            metasDoMes[indice].merge(corpo) { $1 }
            return (200, meta(of: metasDoMes[indice], mes: Formatos.mes(Date())))
        case ("DELETE", _) where partes.count == 2 && partes[0] == "personal-goals":
            return remover(&metasDoMes)
        case ("GET", "girls-math"):
            return (200, girlsMath.enumerated().map { indice, item in
                var linha = item
                linha["data"] = Formatos.dia(Calendar.current.date(byAdding: .day, value: -indice * 3, to: Date())!)
                linha["fotoAssetId"] = NSNull()
                return linha
            })
        case ("POST", "girls-math"):
            let item: [String: Any] = ["id": Self.novoId(), "produto": corpo["produto"] ?? "", "loja": corpo["loja"] ?? NSNull(),
                                       "valor": corpo["valor"] ?? 0, "temFoto": false]
            girlsMath.insert(item, at: 0)
            var linha = item
            linha["data"] = Formatos.dia(Date())
            return (201, linha)
        case ("DELETE", _) where partes.count == 2 && partes[0] == "girls-math":
            return remover(&girlsMath)
        case ("GET", "coins"):
            return (200, saldo)
        case ("POST", "coins/adjustments"):
            let valor = corpo["valor"] as? Int ?? 0
            moedas += valor
            movimentos.insert(["id": Self.novoId(), "valor": valor, "motivo": corpo["motivo"] ?? ""], at: 0)
            return (200, saldo)
        case ("GET", "prizes"):
            return (200, premios.filter { consulta("todos") == "true" || $0["ativo"] as? Bool == true })
        case ("POST", "prizes"):
            var novo = corpo
            novo["id"] = Self.novoId()
            premios.append(novo)
            return (201, novo)
        case ("DELETE", _) where partes.count == 2 && partes[0] == "prizes":
            if let indice = premios.firstIndex(where: { $0["id"] as? String == partes[1] }) { premios[indice]["ativo"] = false }
            return (204, nil)
        case ("GET", "prizes/redemptions"):
            return (200, resgates)
        case ("POST", _) where partes.count == 3 && partes[0] == "prizes" && partes[2] == "redemptions":
            guard let premio = premios.first(where: { $0["id"] as? String == partes[1] }) else { return (404, nil) }
            let preco = premio["preco"] as? Int ?? 0
            guard moedas >= preco else { return (422, ["erro": "Fofocoins insuficientes.", "codigo": "SALDO"]) }
            moedas -= preco
            movimentos.insert(["id": Self.novoId(), "valor": -preco, "motivo": "Resgate: \(premio["nome"] ?? "")"], at: 0)
            let resgate: [String: Any] = ["id": Self.novoId(), "premio": premio["nome"] ?? "", "preco": preco, "usuario": eu,
                                          "status": "PENDENTE", "data": Formatos.dia(Date()), "saldo": moedas]
            resgates.insert(resgate, at: 0)
            return (201, resgate)
        case ("GET", "family"):
            return (200, familia(eu))
        case ("PUT", "family"):
            nomeDaFamilia = corpo["nome"] as? String ?? nomeDaFamilia
            return (200, familia(eu))
        case ("POST", "family/invites"):
            convites.append(["id": Self.novoId(), "email": corpo["email"] ?? "",
                             "venceEm": Formatos.dia(Calendar.current.date(byAdding: .day, value: 7, to: Date())!)])
            return (201, familia(eu))
        case ("DELETE", _) where partes.count == 3 && partes[0] == "family" && partes[1] == "invites":
            convites.removeAll { $0["id"] as? String == partes[2] }
            return (200, familia(eu))
        case ("GET", "family/invites/mine"):
            return (200, [Any]())
        case ("PUT", _) where partes.count == 4 && partes[1] == "members", ("DELETE", _) where partes.count == 3 && partes[1] == "members":
            return (200, familia(eu))
        case ("POST", "family/leave"), ("POST", "family/partnership/accept"):
            return (204, nil)
        case ("POST", "family/partnership/decline"):
            return (200, familia(eu))
        case ("GET", "history"):
            return (200, [
                ["id": 2, "autor": Self.parceira, "acao": "CRIOU", "entidade": "gasto", "entidadeId": NSNull(),
                 "antes": NSNull(), "depois": "Cinema · R$ 64,00", "quando": ISO8601DateFormatter().string(from: Date().addingTimeInterval(-3600))],
                ["id": 1, "autor": eu, "acao": "EDITOU", "entidade": "orcamento", "entidadeId": NSNull(),
                 "antes": "R$ 300,00", "depois": "R$ 380,00", "quando": ISO8601DateFormatter().string(from: Date().addingTimeInterval(-86_400))],
            ])
        case ("GET", "family/history"):
            return (200, [["id": 3, "autor": eu, "acao": "CONVIDOU", "entidade": "pessoa", "entidadeId": NSNull(),
                           "antes": NSNull(), "depois": "joao@exemplo.com",
                           "quando": ISO8601DateFormatter().string(from: Date().addingTimeInterval(-7200))]])
        case ("GET", "exchange-rate/currencies"):
            return (200, [["codigo": "BRL", "nome": "Real brasileiro"], ["codigo": "EUR", "nome": "Euro"],
                          ["codigo": "USD", "nome": "Dólar americano"], ["codigo": "GBP", "nome": "Libra esterlina"],
                          ["codigo": "ARS", "nome": "Peso argentino"]])
        case ("GET", "me/export"):
            return (200, Data(#"{"conta":{"nome":"Pessoa de Demonstração"},"gastos":[]}"#.utf8))
        case ("DELETE", "receipts/consent"):
            return (204, nil)
        case ("DELETE", "assistant/consent"):
            assistenteAutorizado = false
            return (204, nil)
        case ("GET", "assistant"):
            return (200, situacaoDoAssistente())
        case ("POST", "assistant/consent"):
            assistenteAutorizado = true
            return (200, situacaoDoAssistente())
        case ("POST", "assistant/ask"):
            // Na demonstracao nao ha IA: uma resposta de exemplo com o gasto do mes.
            assistenteAutorizado = true
            perguntasAoAssistente += 1
            let total = gastos.filter { ($0["data"] as? String)?.hasPrefix(mes) == true }
                .reduce(0.0) { $0 + ($1["valor"] as? Double ?? 0) }
            let texto = "Neste mês você gastou \(Formatos.moeda(Decimal(total))). (Na demonstração as respostas são de exemplo; no app, a IA responde a sua pergunta.)"
            return (200, ["texto": texto, "tela": "relatorios",
                          "restantes": situacaoDoAssistente()["restantes"] ?? 0])
        case ("GET", "users"):
            return (200, [Any]())
        case ("GET", "shopping/items"):
            return (200, compras)
        case ("POST", "shopping/items"):
            var novo = corpo
            novo["id"] = Self.novoId()
            novo["comprado"] = false
            for chave in ["marca", "imagem", "codigo"] where novo[chave] == nil { novo[chave] = "" }
            compras.insert(novo, at: 0)
            return (201, novo)
        case ("PATCH", _) where partes.count == 4 && partes[1] == "items" && partes[3] == "purchased":
            guard let indice = compras.firstIndex(where: { $0["id"] as? String == partes[2] }) else { return (404, nil) }
            compras[indice]["comprado"] = consulta("comprado") == "true"
            return (200, compras[indice])
        case ("DELETE", _) where partes.count == 3 && partes[1] == "items":
            return remover(&compras)
        case ("GET", "shopping/catalog"):
            let termo = consulta("busca") ?? ""
            return (200, [["codigo": "7891000100103", "nome": termo.capitalized + " integral", "marca": "Nestlé",
                           "quantidade": "1 L", "imagem": ""],
                          ["codigo": "7891000053508", "nome": termo.capitalized + " em pó", "marca": "Ninho",
                           "quantidade": "400 g", "imagem": ""]])
        case ("GET", "places"):
            return (200, lugares.enumerated().map { indice, lugar in
                var linha = lugar
                linha["data"] = Formatos.dia(Calendar.current.date(byAdding: .day, value: -indice * 9, to: Date())!)
                linha["fotoAssetId"] = NSNull()
                linha["usuario"] = eu
                return linha
            })
        case ("GET", "places/tags"):
            return (200, marcadores)
        case ("POST", "places/tags"):
            if let nome = corpo["nome"] as? String { marcadores.append(nome) }
            return (201, marcadores)
        case ("POST", "places"), ("PUT", _) where partes.count == 2 && partes[0] == "places":
            var lugar = corpo
            lugar["id"] = partes.count == 2 ? partes[1] : Self.novoId()
            lugar["temFoto"] = false
            lugar["fotoAssetId"] = NSNull()
            lugar["usuario"] = eu
            lugar.removeValue(forKey: "imageBase64")
            lugar.removeValue(forKey: "mimeType")
            lugares.removeAll { $0["id"] as? String == lugar["id"] as? String }
            lugares.insert(lugar, at: 0)
            return (metodo == "POST" ? 201 : 200, lugar)
        case ("DELETE", _) where partes.count == 2 && partes[0] == "places":
            return remover(&lugares)
        case ("GET", "movies"):
            return (200, filmes.map { var linha = $0; linha["usuario"] = eu; return linha })
        case ("GET", "movies/search"), ("GET", "movies/random"):
            let catalogo: [[String: Any]] = [
                ["tmdbId": "680", "titulo": "Pulp Fiction", "ano": "1994", "poster": "", "nota": 8.5,
                 "sinopse": "Histórias de crime em Los Angeles que se cruzam de um jeito inesperado."],
                ["tmdbId": "238", "titulo": "O Poderoso Chefão", "ano": "1972", "poster": "", "nota": 8.7,
                 "sinopse": "A saga da família Corleone."],
            ]
            return (200, partes.last == "random" ? catalogo[0] : catalogo)
        case ("POST", "movies"):
            var filme = corpo
            filme["id"] = Self.novoId()
            filme["assistido"] = false
            filme["avaliacoes"] = [String: Int]()
            filmes.insert(filme, at: 0)
            return (201, filme)
        case ("PATCH", _) where partes.count == 3 && partes[2] == "watched":
            guard let indice = filmes.firstIndex(where: { $0["id"] as? String == partes[1] }) else { return (404, nil) }
            filmes[indice]["assistido"] = consulta("assistido") == "true"
            return (200, filmes[indice])
        case ("PUT", _) where partes.count == 3 && partes[2] == "rating":
            guard let indice = filmes.firstIndex(where: { $0["id"] as? String == partes[1] }) else { return (404, nil) }
            var notas = filmes[indice]["avaliacoes"] as? [String: Int] ?? [:]
            notas[eu] = corpo["nota"] as? Int
            filmes[indice]["avaliacoes"] = notas
            return (200, filmes[indice])
        case ("DELETE", _) where partes.count == 2 && partes[0] == "movies":
            return remover(&filmes)
        case ("GET", "feed"):
            return (200, feed.map { foto in
                var linha = foto
                linha["mesKey"] = mes
                linha["usuario"] = eu
                linha["criadoEm"] = ISO8601DateFormatter().string(from: Date())
                return linha
            })
        case ("POST", "feed"):
            let foto: [String: Any] = ["id": Self.novoId(), "assetId": "demo-foto-\(feed.count + 1)", "legenda": "",
                                       "mesKey": corpo["mesKey"] ?? mes, "usuario": eu,
                                       "criadoEm": ISO8601DateFormatter().string(from: Date())]
            feed.insert(foto, at: 0)
            return (201, foto)
        case ("PATCH", _) where partes.count == 3 && partes[0] == "feed":
            guard let indice = feed.firstIndex(where: { $0["id"] as? String == partes[1] }) else { return (404, nil) }
            feed[indice]["legenda"] = corpo["legenda"]
            var linha = feed[indice]
            linha["mesKey"] = mes
            linha["criadoEm"] = ISO8601DateFormatter().string(from: Date())
            return (200, linha)
        case ("DELETE", _) where partes.count == 2 && partes[0] == "feed":
            return remover(&feed)
        case ("GET", _) where partes.count == 3 && partes[0] == "assets":
            return (200, Self.imagemInventada(partes[1]))
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
