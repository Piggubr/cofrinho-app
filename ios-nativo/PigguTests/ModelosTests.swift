import Foundation
import Testing
@testable import Piggu

/// JSON no formato que o backend devolve (records do Spring serializados pelo Jackson).
struct ModelosTests {
    private let decodificador = JSONDecoder()

    @Test func decodificaParDeTokensComPerfilCompleto() throws {
        let json = """
        {
          "accessToken": "eyJhbGciOiJSUzI1NiJ9.abc.def",
          "expiresIn": 1800,
          "usuario": {
            "id": "3f2b9a6e-1c2d-4e5f-8a9b-0c1d2e3f4a5b",
            "email": "ana@example.com",
            "nome": "Ana Souza",
            "primeiroNome": "Ana",
            "apelido": null,
            "foto": null,
            "role": "TITULAR",
            "ativo": true,
            "permissoes": {"assistente": true},
            "preferencias": {"moeda": "BRL"},
            "plano": "PREMIUM",
            "premiumAte": "2027-01-31T23:59:59Z",
            "familia": "9e8d7c6b-5a4f-4e3d-8c2b-1a0f9e8d7c6b",
            "avisos": {"email": true, "push": false},
            "cpfInformado": true,
            "fotoPropria": false,
            "temFotoDoGoogle": false,
            "termosAceitos": "2026-05",
            "temSenha": true,
            "emailConfirmado": true,
            "pais": "BR",
            "nascimento": "1992-03-14",
            "compartilhamento": {"mostraTudoAoParceiro": false, "compartilharPorPadrao": true}
          }
        }
        """
        let tokens = try decodificador.decode(ParDeTokens.self, from: Data(json.utf8))
        #expect(tokens.accessToken == "eyJhbGciOiJSUzI1NiJ9.abc.def")
        #expect(tokens.expiresIn == 1800)
        #expect(tokens.usuario.role == .titular)
        #expect(tokens.usuario.plano == .premium)
        #expect(tokens.usuario.apelido == nil)
        #expect(tokens.usuario.comoChamar == "Ana")
        #expect(tokens.usuario.nascimento == "1992-03-14")
    }

    @Test func decodificaGastosComCamposOpcionaisAusentesOuNulos() throws {
        let json = """
        [
          {
            "id": "11111111-1111-4111-8111-111111111111",
            "data": "2026-10-05",
            "reciboId": null,
            "estabelecimento": "Padaria",
            "item": "Pão francês",
            "categoria": "Mercado",
            "valor": 12.34,
            "tipo": "Variavel",
            "origem": "manual",
            "usuario": "3f2b9a6e-1c2d-4e5f-8a9b-0c1d2e3f4a5b",
            "registradoEm": "2026-10-05T12:30:00.123456Z",
            "contaId": null,
            "parcela": 2,
            "parcelas": 10,
            "moedaOriginal": null,
            "valorOriginal": null,
            "compartilhado": false
          },
          {
            "id": "22222222-2222-4222-8222-222222222222",
            "data": "2026-10-04",
            "item": "Netflix",
            "categoria": "Lazer",
            "valor": 39.9,
            "usuario": null,
            "moedaOriginal": "USD",
            "valorOriginal": 7.99
          }
        ]
        """
        let gastos = try decodificador.decode([Gasto].self, from: Data(json.utf8))
        #expect(gastos.count == 2)
        #expect(gastos[0].valor == Decimal(string: "12.34"))
        #expect(gastos[0].parcela == 2)
        #expect(gastos[0].parcelas == 10)
        #expect(gastos[0].compartilhado == false)
        #expect(gastos[1].estabelecimento == nil)
        #expect(gastos[1].usuario == nil)
        #expect(gastos[1].valorOriginal == Decimal(string: "7.99"))
    }

    @Test func decodificaResumoDoMesComNulos() throws {
        let json = """
        {
          "mes": "2026-10",
          "receitas": 0,
          "gastos": 850.10,
          "sobra": -850.10,
          "taxaDePoupanca": null,
          "gastosMesAnterior": 0,
          "variacao": null,
          "projecaoDeGastos": 1210.5,
          "porCategoria": [
            {"categoria": "Mercado", "total": 600.10, "anterior": 0},
            {"categoria": "Transporte", "total": 250, "anterior": 0}
          ]
        }
        """
        let resumo = try decodificador.decode(ResumoDoMes.self, from: Data(json.utf8))
        #expect(resumo.mes == "2026-10")
        #expect(resumo.sobra == Decimal(string: "-850.10"))
        #expect(resumo.taxaDePoupanca == nil)
        #expect(resumo.variacao == nil)
        #expect(resumo.projecaoDeGastos == Decimal(string: "1210.5"))
        #expect(resumo.porCategoria.map(\.categoria) == ["Mercado", "Transporte"])
    }

    @Test func decodificaCategoriasEErro() throws {
        let categorias = try decodificador.decode(
            Categorias.self, from: Data(#"{"categorias":["Casa","Mercado","Outros"]}"#.utf8))
        #expect(categorias.categorias == ["Casa", "Mercado", "Outros"])

        let erro = try decodificador.decode(CorpoDeErro.self, from: Data("""
            {"erro":"E-mail ou senha incorretos.","codigo":"CREDENCIAIS","campos":[],"momento":"2026-10-23T19:00:00Z"}
            """.utf8))
        #expect(erro.erro == "E-mail ou senha incorretos.")
    }

    @Test func codificaNovoLancamentoComOsNomesDoBackend() throws {
        let pedido = NovoLancamento(
            data: "2026-10-23", estabelecimento: nil,
            itens: [ItemDeGasto(item: "Almoço", categoria: "Alimentação", valor: Decimal(string: "42.5")!)])
        let dados = try JSONEncoder().encode(pedido)
        let json = try #require(try JSONSerialization.jsonObject(with: dados) as? [String: Any])
        #expect(json["data"] as? String == "2026-10-23")
        // Nulo nao vai: o backend aplica o padrao.
        #expect(json.keys.contains("estabelecimento") == false)
        let itens = try #require(json["itens"] as? [[String: Any]])
        #expect(itens.first?["item"] as? String == "Almoço")
        #expect(itens.first?["categoria"] as? String == "Alimentação")
        #expect((itens.first?["valor"] as? NSNumber)?.decimalValue == Decimal(string: "42.5"))
    }

    @Test func lancamentoSoLevaOQueFoiPreenchido() throws {
        let pedido = NovoLancamento(
            data: "2026-10-27", origem: "Manual",
            itens: [ItemDeGasto(item: "Fone", categoria: nil, valor: Decimal(string: "299.9")!, tipo: "Variavel")],
            contaId: "c1", parcelas: 3, compartilhado: false, idDoEnvio: "e1")
        let json = try JSONSerialization.jsonObject(with: JSONEncoder().encode(pedido)) as? [String: Any]
        #expect(Set(json?.keys.map { $0 } ?? []) == ["data", "origem", "itens", "contaId", "parcelas", "compartilhado", "idDoEnvio"])
        let item = (json?["itens"] as? [[String: Any]])?.first
        #expect(Set(item?.keys.map { $0 } ?? []) == ["item", "valor", "tipo"])
    }

    @Test func decodificaOQueOPainelMostra() throws {
        let objetivo = try decodificador.decode(ObjetivoDoCofrinho.self, from: Data("""
        {"id":"o1","nome":"Viagem","alvo":3000,"prazo":"2027-04-30","guardado":1150.5,"falta":1849.5,
         "percentual":38,"mesesRestantes":6,"porMes":308.25,"concluido":false,"atrasado":false,
         "usuario":"u1","compartilhado":true}
        """.utf8))
        #expect(objetivo.porMes == Decimal(string: "308.25"))
        #expect(objetivo.percentual == 38)

        let cartao = try decodificador.decode(ContaOuCartao.self, from: Data("""
        {"id":"c1","nome":"Nubank","tipo":"CARTAO","fechamento":3,"vencimento":10,"faturaAberta":null,
         "faturaAPagar":{"mes":"2026-10","inicio":"2026-09-03","ultimaCompra":"2026-10-02",
         "fechamento":"2026-10-03","vencimento":"2026-10-10","total":1234.56},"compartilhado":false}
        """.utf8))
        #expect(cartao.tipo == .cartao)
        #expect(cartao.faturaAPagar?.total == Decimal(string: "1234.56"))

        let orcamento = try decodificador.decode(Orcamento.self, from: Data("""
        {"id":"b1","categoria":"Mercado","limite":380,"gasto":312.47,"percentual":82.2,"alerta":"ATENCAO"}
        """.utf8))
        #expect(orcamento.alerta == .atencao)

        let metas = try decodificador.decode([String: Decimal].self, from: Data(#"{"2026-10":2500.5}"#.utf8))
        #expect(metas["2026-10"] == Decimal(string: "2500.5"))
    }

    @Test func decodificaReceitasEAReceber() throws {
        let receita = try decodificador.decode(Receita.self, from: Data("""
        {"id":"r1","data":"2026-10-01","descricao":"Salário","categoria":"Salário","valor":5200,
         "usuario":"u1","compartilhado":false}
        """.utf8))
        #expect(receita.valor == 5200)
        let conta = try decodificador.decode(AReceber.self, from: Data("""
        {"id":"a1","quem":"João","descricao":"","valor":45,"data":"2026-10-24"}
        """.utf8))
        #expect(conta.quem == "João")
        let fixa = try decodificador.decode(ContaFixa.self, from: Data("""
        {"id":"f1","descricao":"Salário","categoria":"Salário","valor":5200,"dia":1,"automatico":true,
         "vencimento":"2026-10-01","situacao":"PAGA","tipo":"RECEITA","contaId":null,"usuario":"u1"}
        """.utf8))
        #expect(fixa.situacao == .paga)
    }
}
