import UIKit
import Capacitor

/**
 * Tela principal do app: o WebView do Capacitor com os plugins locais registrados.
 *
 * Plugins escritos aqui no projeto (fora de pacotes npm) precisam ser registrados a mao;
 * o capacitorDidLoad roda antes de a pagina carregar, entao o JS ja os encontra.
 */
class ViewControllerDoApp: CAPBridgeViewController {
    override open func capacitorDidLoad() {
        bridge?.registerPluginInstance(AbasNativasPlugin())
    }
}
