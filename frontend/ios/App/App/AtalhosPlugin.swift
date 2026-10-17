import Foundation
import Capacitor

/**
 * Atalhos que abrem o app numa tela: o "Lancar gasto" do icone (toque longo), a Siri e o
 * Atalhos do iOS, e os links piggu://.
 *
 * O atalho pode chegar antes de a pagina carregar (app fechado). Por isso a rota fica
 * guardada ate o Angular pedir (pendente); com a pagina ja aberta, vai direto pelo
 * evento "abrir".
 */
@objc(AtalhosPlugin)
public class AtalhosPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "AtalhosPlugin"
    public let jsName = "Atalhos"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "pendente", returnType: CAPPluginReturnPromise),
    ]

    /** Tipo do atalho estatico do icone, igual ao do Info.plist. */
    static let atalhoDeGasto = "br.com.piggu.app.lancar-gasto"
    static let rotaDeGasto = "/gastos?rapido=1"

    private static var instancia: AtalhosPlugin?
    private static var guardada: String?

    override public func load() {
        AtalhosPlugin.instancia = self
    }

    @objc func pendente(_ call: CAPPluginCall) {
        let rota = AtalhosPlugin.guardada
        AtalhosPlugin.guardada = nil
        call.resolve(rota.map { ["rota": $0] } ?? [:])
    }

    /** Abre a rota no app; so aceita caminho interno, nunca endereco de fora. */
    static func abrir(_ rota: String) {
        guard rota.hasPrefix("/"), !rota.hasPrefix("//") else { return }
        DispatchQueue.main.async {
            if let plugin = instancia, plugin.bridge?.webView != nil {
                plugin.notifyListeners("abrir", data: ["rota": rota], retainUntilConsumed: true)
            } else {
                guardada = rota
            }
        }
    }

    /** piggu://gastos?rapido=1 vira /gastos?rapido=1. */
    static func abrir(url: URL) -> Bool {
        guard url.scheme == "piggu" else { return false }
        var rota = "/" + (url.host ?? "") + url.path
        if let consulta = url.query, !consulta.isEmpty {
            rota += "?" + consulta
        }
        abrir(rota)
        return true
    }
}
