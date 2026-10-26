import Foundation
import Testing
@testable import Piggu

@MainActor
struct CacheTests {
    private let gasto = Gasto(
        id: "g1", data: "2026-10-26", reciboId: nil, estabelecimento: "Feira", item: "Banana",
        categoria: "Mercado", valor: Decimal(string: "12.5")!, tipo: nil, origem: nil, usuario: nil,
        registradoEm: nil, contaId: nil, parcela: nil, parcelas: nil, moedaOriginal: nil,
        valorOriginal: nil, compartilhado: nil)

    @Test func guardaELeDeVoltaOValorExato() {
        let cache = Cache(soNaMemoria: true)
        let quando = Date(timeIntervalSince1970: 1_800_000_000)

        cache.guardar([gasto], em: "gastos/2026-10", agora: quando)
        let lido = cache.ler("gastos/2026-10", como: [Gasto].self)

        #expect(lido?.valor == [gasto])
        #expect(lido?.em == quando)
        #expect(cache.ler("gastos/2026-09", como: [Gasto].self) == nil)
    }

    @Test func mostraOGuardadoEDepoisONovo() async {
        let cache = Cache(soNaMemoria: true)
        cache.guardar([Gasto](), em: "gastos/2026-10")
        var mostrados: [[Gasto]?] = []

        await cache.carregar("gastos/2026-10", mostrar: { mostrados.append($0.valor) }) { [gasto] in [gasto] }

        #expect(mostrados == [[], [gasto]])
        #expect(cache.ler("gastos/2026-10", como: [Gasto].self)?.valor == [gasto])
    }

    @Test func semRedeFicaOGuardadoComAHora() async {
        let cache = Cache(soNaMemoria: true)
        let quando = Date(timeIntervalSince1970: 1_800_000_000)
        cache.guardar([gasto], em: "gastos/2026-10", agora: quando)
        var ultimo: Carga<[Gasto]>?

        await cache.carregar("gastos/2026-10", mostrar: { ultimo = $0 }) { throw ErroDaAPI.semConexao }

        guard case let .pronto(valor, guardadoEm) = ultimo else {
            Issue.record("esperava o guardado, veio \(String(describing: ultimo))")
            return
        }
        #expect(valor == [gasto])
        #expect(guardadoEm == quando)
    }

    @Test func semRedeESemNadaGuardadoEErro() async {
        let cache = Cache(soNaMemoria: true)
        var ultimo: Carga<[Gasto]>?

        await cache.carregar("gastos/2026-10", mostrar: { ultimo = $0 }) { throw ErroDaAPI.semConexao }

        guard case .falhou(.semConexao) = ultimo else {
            Issue.record("esperava sem conexao, veio \(String(describing: ultimo))")
            return
        }
    }

    @Test func apagarTudoLimpaOCache() {
        let cache = Cache(soNaMemoria: true)
        cache.guardar([gasto], em: "gastos/2026-10")

        cache.apagarTudo()

        #expect(cache.ler("gastos/2026-10", como: [Gasto].self) == nil)
    }
}

struct KeychainTests {
    @Test func guardaTrocaEApaga() {
        let cofre = Keychain(servico: "br.com.piggu.app.testes", conta: UUID().uuidString)
        defer { cofre.apagar() }

        #expect(cofre.ler() == nil)
        cofre.guardar("r1")
        #expect(cofre.ler() == "r1")
        cofre.guardar("r2")
        #expect(cofre.ler() == "r2")
        cofre.apagar()
        #expect(cofre.ler() == nil)
    }
}
