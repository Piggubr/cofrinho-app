import Network
import Observation

/// Se o aparelho tem rede agora (Wi-Fi, dados moveis ou cabo). As telas recarregam quando a
/// rede volta e mostram "sem internet" sem esperar o pedido falhar.
@MainActor
@Observable
final class MonitorDeRede {
    private(set) var conectado = true

    @ObservationIgnored private let monitor = NWPathMonitor()

    init() {
        monitor.pathUpdateHandler = { [weak self] caminho in
            let conectado = caminho.status == .satisfied
            Task { @MainActor in self?.conectado = conectado }
        }
        monitor.start(queue: DispatchQueue(label: "br.com.piggu.app.rede"))
    }

    deinit {
        monitor.cancel()
    }
}
