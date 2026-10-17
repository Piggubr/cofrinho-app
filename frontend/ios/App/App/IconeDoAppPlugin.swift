import UIKit
import Capacitor

/**
 * Troca o icone do app na tela inicial (Premium). Os icones alternativos sao os conjuntos
 * AppIcon-<id> do Assets.xcassets, gerados por scripts/gerar-icones.mjs; cada um traz a
 * versao escura, que o iOS 18 usa sozinho no modo escuro.
 *
 * O iOS mostra um aviso a cada troca e nao deixa esconder. Contrato igual ao do Android.
 */
@objc(IconeDoAppPlugin)
public class IconeDoAppPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "IconeDoAppPlugin"
    public let jsName = "IconeDoApp"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "atual", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "trocar", returnType: CAPPluginReturnPromise),
    ]

    private static let prefixo = "AppIcon-"

    @objc func atual(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            if let nome = UIApplication.shared.alternateIconName {
                call.resolve(["nome": String(nome.dropFirst(IconeDoAppPlugin.prefixo.count))])
            } else {
                call.resolve(["nome": NSNull()])
            }
        }
    }

    @objc func trocar(_ call: CAPPluginCall) {
        let id = call.getString("nome")
        // So letras: o id vira nome de recurso do app, nunca caminho.
        if let id = id, id.isEmpty || !id.allSatisfy({ $0.isLetter }) {
            call.reject("Ícone desconhecido")
            return
        }
        DispatchQueue.main.async {
            guard UIApplication.shared.supportsAlternateIcons else {
                call.reject("Este aparelho não troca o ícone")
                return
            }
            UIApplication.shared.setAlternateIconName(id.map { IconeDoAppPlugin.prefixo + $0 }) { erro in
                if let erro = erro {
                    call.reject(erro.localizedDescription)
                } else {
                    call.resolve()
                }
            }
        }
    }
}
