import Foundation
import Testing
@testable import Piggu

/// A regra do banner do plano gratuito (D-180 e D-203), a mesma do Android.
struct AnunciosTests {
    private func usuario(role: PigguRole = .titular, plano: Plano = .gratuito, nascimento: String? = "1990-05-10") -> Usuario {
        Usuario(id: "u1", email: "ana@piggu.app", nome: "Ana Souza", primeiroNome: "Ana", apelido: "Ana", foto: nil,
                role: role, ativo: true, plano: plano, premiumAte: nil, familia: "f1", emailConfirmado: true,
                pais: "BR", nascimento: nascimento)
    }

    /// Meio-dia em Brasilia no dia pedido.
    private func dia(_ ano: Int, _ mes: Int, _ dia: Int, hora: Int = 12) -> Date {
        var calendario = Calendar(identifier: .gregorian)
        calendario.timeZone = TimeZone(identifier: "America/Sao_Paulo")!
        return calendario.date(from: DateComponents(year: ano, month: mes, day: dia, hour: hora))!
    }

    @Test func gratuitoAdultoVeAnuncio() {
        #expect(Conta.deveMostrarAnuncios(usuario(), demo: false, hoje: dia(2026, 11, 26)))
        #expect(Conta.deveMostrarAnuncios(usuario(role: .parceiro), demo: false, hoje: dia(2026, 11, 26)))
        #expect(Conta.deveMostrarAnuncios(usuario(role: .membro), demo: false, hoje: dia(2026, 11, 26)))
    }

    @Test func premiumAdminDemoESemUsuarioNaoVeem() {
        let hoje = dia(2026, 11, 26)
        #expect(!Conta.deveMostrarAnuncios(usuario(plano: .premium), demo: false, hoje: hoje))
        #expect(!Conta.deveMostrarAnuncios(usuario(role: .admin), demo: false, hoje: hoje))
        #expect(!Conta.deveMostrarAnuncios(usuario(), demo: true, hoje: hoje))
        #expect(!Conta.deveMostrarAnuncios(nil, demo: false, hoje: hoje))
    }

    @Test func menorDeIdadeNaoVe() {
        // Faz 18 em 27/11/2026: na vespera ainda nao ve, no dia ja ve.
        let quase = usuario(nascimento: "2008-11-27")
        #expect(!Conta.deveMostrarAnuncios(quase, demo: false, hoje: dia(2026, 11, 26)))
        #expect(Conta.deveMostrarAnuncios(quase, demo: false, hoje: dia(2026, 11, 27)))
        #expect(!Conta.deveMostrarAnuncios(usuario(nascimento: "2015-01-01"), demo: false, hoje: dia(2026, 11, 26)))
    }

    @Test func aniversarioContaNoFusoDeBrasilia() {
        // 23h de 26/11 em Brasilia ja e 27/11 em UTC: ainda nao fez 18.
        let quase = usuario(nascimento: "2008-11-27")
        #expect(!Conta.deveMostrarAnuncios(quase, demo: false, hoje: dia(2026, 11, 26, hora: 23)))
    }

    @Test func nascidoEm29DeFevereiroFaz18Em28() {
        let bissexto = usuario(nascimento: "2008-02-29")
        #expect(!Conta.deveMostrarAnuncios(bissexto, demo: false, hoje: dia(2026, 2, 27)))
        #expect(Conta.deveMostrarAnuncios(bissexto, demo: false, hoje: dia(2026, 2, 28)))
    }

    @Test func nascimentoAusenteOuIlegivelNaoMostra() {
        let hoje = dia(2026, 11, 26)
        for nascimento in [nil, "", "10/05/1990", "1990-13-01", "1990-02-30", "abc"] as [String?] {
            #expect(!Conta.deveMostrarAnuncios(usuario(nascimento: nascimento), demo: false, hoje: hoje))
        }
    }

    /// Assinou o Premium ou trocou de conta: a leitura segue a sessao, sem reabrir o app.
    @MainActor @Test func segueASessaoNaHora() {
        let sessao = Sessao(cliente: ClienteDaAPI(transporte: TransporteDeDemonstracao()), cache: Cache(soNaMemoria: true))
        let hoje = dia(2026, 11, 26)
        #expect(!Conta.deveMostrarAnuncios(sessao.usuario, demo: false, hoje: hoje))
        sessao.atualizar(usuario())
        #expect(Conta.deveMostrarAnuncios(sessao.usuario, demo: false, hoje: hoje))
        sessao.atualizar(usuario(plano: .premium))
        #expect(!Conta.deveMostrarAnuncios(sessao.usuario, demo: false, hoje: hoje))
        sessao.atualizar(usuario(nascimento: "2012-03-01"))
        #expect(!Conta.deveMostrarAnuncios(sessao.usuario, demo: false, hoje: hoje))
    }
}
