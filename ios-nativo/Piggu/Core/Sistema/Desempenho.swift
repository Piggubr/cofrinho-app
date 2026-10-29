import os

/// Intervalo "Abrir ate o Painel" (do init do app ao Painel com dados), nos Points of Interest
/// do Instruments e no teste de desempenho. A meta com cache e 1 s.
enum Desempenho {
    static let subsistema = "br.com.piggu.app"
    static let categoria = "PointsOfInterest"
    static let nome: StaticString = "Abrir ate o Painel"

    private static let sinais = OSSignposter(subsystem: subsistema, category: .pointsOfInterest)
    nonisolated(unsafe) private static var abertura: OSSignpostIntervalState?

    @MainActor
    static func abrindo() {
        abertura = sinais.beginInterval(nome)
    }

    /// Chamado a cada vez que o Painel tem dados; so a primeira fecha o intervalo.
    @MainActor
    static func painelPronto() {
        guard let estado = abertura else { return }
        sinais.endInterval(nome, estado)
        abertura = nil
    }
}
