import Foundation
import Testing
import UIKit
@testable import Piggu

struct ContaEMetasTests {
    private func meta(_ titulo: String, _ atual: Int, de alvo: Int) -> MetaDoMes {
        MetaDoMes(id: titulo, titulo: titulo, alvo: alvo, atual: atual, mes: "2026-10", concluida: atual >= alvo)
    }

    @Test func percentualContaAsConcluidasNaoOProgresso() {
        #expect(MinhasMetasView.percentualConcluido([]) == 0)
        // Uma meta grande quase pronta nao esconde as pequenas: 1 de 3 concluidas.
        #expect(MinhasMetasView.percentualConcluido([meta("A", 1, de: 1), meta("B", 99, de: 100), meta("C", 0, de: 1)]) == 33)
        #expect(MinhasMetasView.mensagem([]) == "Crie sua jornada de metas.")
        #expect(MinhasMetasView.mensagem([meta("A", 1, de: 1)]) == "Você concluiu todas as metas do mês.")
        #expect(MinhasMetasView.mensagem([meta("A", 0, de: 1)]) == "Continue no seu ritmo.")
    }

    @Test func textoParaCompartilharSemDinheiro() {
        let texto = MinhasMetasView.textoParaCompartilhar(
            [meta("Ler", 1, de: 2), meta("Caminhar", 3, de: 3)], rotuloDoMes: "Outubro de 2026")
        #expect(texto == "Minhas metas de Outubro de 2026:\n⏳ Ler: 1 de 2\n✅ Caminhar: 3 de 3")
    }

    @Test func fotoDePerfilViraQuadradoDe320() throws {
        let grande = UIGraphicsImageRenderer(size: CGSize(width: 1200, height: 800)).image { contexto in
            UIColor.red.setFill()
            contexto.fill(CGRect(x: 0, y: 0, width: 1200, height: 800))
        }
        let jpeg = try #require(Imagem.fotoDePerfil(grande))
        let lida = try #require(UIImage(data: jpeg))
        #expect(lida.size.width * lida.scale == 320)
        #expect(lida.size.height * lida.scale == 320)

        let pequena = UIGraphicsImageRenderer(size: CGSize(width: 100, height: 200),
                                              format: { let f = UIGraphicsImageRendererFormat(); f.scale = 1; return f }())
            .image { _ in }
        let lado = try #require(Imagem.fotoDePerfil(pequena).flatMap(UIImage.init(data:)))
        // Menor que 320 nao aumenta.
        #expect(lado.size.width * lado.scale == 100)
    }

    @Test func fotoPropriaVemComoDataURL() throws {
        let png = UIGraphicsImageRenderer(size: CGSize(width: 2, height: 2)).pngData { _ in }
        #expect(Imagem.deDataURL("data:image/png;base64," + png.base64EncodedString()) != nil)
        #expect(Imagem.deDataURL("https://lh3.googleusercontent.com/a/foto") == nil)
    }

    @Test func historicoDaFamiliaComONomeDeQuemFez() {
        let membros = [MembroDaFamilia(id: "u2", nome: "Bia", papel: .parceiro)]
        let apagou = EventoDeAuditoria(id: 1, autor: "u2", acao: "APAGOU", entidade: "gasto", antes: "Cinema", depois: nil,
                                       quando: "2026-10-27T12:00:00Z")
        #expect(FamiliaView.descrever(apagou, membros: membros) == "Bia apagou gasto")
        #expect(FamiliaView.mudanca(apagou) == "Cinema")
        let saiu = EventoDeAuditoria(id: 2, autor: "u9", acao: "SAIU", entidade: "pessoa", antes: nil, depois: nil,
                                     quando: "2026-10-27T12:00:00.123Z")
        #expect(FamiliaView.descrever(saiu, membros: membros) == "Ex-membro saiu da família")
        #expect(FamiliaView.mudanca(saiu) == nil)
        #expect(FamiliaView.quando(saiu).contains("2026"))
    }

    @Test func planoDaFamilia() {
        var familia = Familia(membros: [])
        #expect(FamiliaView.plano(familia) == "Gratuito")
        familia.plano = .premium
        #expect(FamiliaView.plano(familia) == "Premium individual")
        familia.planoCasal = true
        #expect(FamiliaView.plano(familia) == "Premium casal")
        familia.planoFamilia = true
        #expect(FamiliaView.plano(familia) == "Premium família")
    }

    @Test func iconesDoAppComoNoSite() {
        #expect(IconeDoApp.allCases.count == 12)
        #expect(IconeDoApp.allCases.filter { !$0.premium } == [.padrao, .classico])
        #expect(IconeDoApp.padrao.nomeAlternativo == nil)
        #expect(IconeDoApp.lilas.nomeAlternativo == "AppIcon-lilas")
        // Cada alternativo existe no catalogo (ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES).
        let alternativos = Bundle.main.object(forInfoDictionaryKey: "CFBundleIcons") as? [String: Any]
        let nomes = ((alternativos?["CFBundleAlternateIcons"] as? [String: Any]) ?? [:]).keys
        #expect(Set(nomes) == Set(IconeDoApp.allCases.compactMap(\.nomeAlternativo)))
    }

    @Test func decodificaFamiliaPremiosEMetas() throws {
        let decodificador = JSONDecoder()
        let familia = try decodificador.decode(Familia.self, from: Data("""
        {"id":"f1","nome":"Casa","plano":"PREMIUM","planoFamilia":false,"planoCasal":true,
         "membros":[{"id":"u1","nome":"Ana","email":"ana@x.com","foto":null,"papel":"TITULAR","premium":true},
                    {"id":"u2","nome":"Bia","email":"bia@x.com","foto":null,"papel":"MEMBRO","premium":false,"parceriaPendente":true}],
         "convites":[{"id":"c1","email":"joao@x.com","venceEm":"2026-11-03"}]}
        """.utf8))
        #expect(familia.membros.last?.parceriaPendente == true)
        #expect(familia.convites?.first?.email == "joao@x.com")

        let saldo = try decodificador.decode(SaldoDeMoedas.self, from: Data("""
        {"saldo":120,"historico":[{"id":"m1","data":"2026-10-20","valor":-60,"motivo":"Resgate","usuario":"USUARIA"}]}
        """.utf8))
        #expect(saldo.historico.first?.valor == -60)

        let meta = try decodificador.decode(MetaDoMes.self, from: Data("""
        {"id":"m1","titulo":"Ler","alvo":2,"atual":1,"mes":"2026-10","concluida":false}
        """.utf8))
        #expect(meta.alvo == 2)

        let item = try decodificador.decode(ItemDoGirlsMath.self, from: Data("""
        {"id":"g1","produto":"Bolsa","loja":"","valor":389.9,"data":"2026-10-27","fotoAssetId":null,"temFoto":false}
        """.utf8))
        #expect(item.valor == Decimal(string: "389.9"))
    }
}
