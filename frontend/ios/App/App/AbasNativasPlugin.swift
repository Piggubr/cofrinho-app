import UIKit
import Capacitor

/**
 * Barra de abas nativa do iOS por cima do WebView.
 *
 * O Angular manda as abas (id, titulo e simbolo SF) e qual esta selecionada; aqui elas viram
 * um UITabBar de verdade. Compilado com o SDK do iOS 26 (Xcode 26), o UITabBar ganha o
 * visual Liquid Glass sozinho; em versoes anteriores fica a barra translucida padrao.
 *
 * O toque numa aba nao navega nada: so avisa o Angular pelo evento "abaTocada", e ele decide
 * (trocar de rota, abrir o "Mais"). A altura da barra vai no evento "alturaMudou" para o CSS
 * reservar o espaco embaixo do conteudo, que continua passando por tras do vidro.
 */
@objc(AbasNativasPlugin)
public class AbasNativasPlugin: CAPPlugin, CAPBridgedPlugin, UITabBarDelegate {
    public let identifier = "AbasNativasPlugin"
    public let jsName = "AbasNativas"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "configurar", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "selecionar", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "mostrar", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "esconder", returnType: CAPPluginReturnPromise),
    ]

    private var barra: BarraDeAbas?
    private var ids: [String] = []
    /** Abas que disparam uma acao (o "+") e por isso nao ficam marcadas depois do toque. */
    private var acoes: Set<String> = []
    private var ultimaAltura: CGFloat = -1
    /** O Angular esconde a barra em tela larga (iPad), onde o menu vira coluna lateral. */
    private var deveMostrar = true

    @objc func configurar(_ call: CAPPluginCall) {
        guard let abas = call.getArray("abas", JSObject.self), !abas.isEmpty else {
            call.reject("Informe ao menos uma aba.")
            return
        }
        let selecionada = call.getString("selecionada")
        let corClara = call.getString("corDestaque")
        let corEscura = call.getString("corDestaqueEscura")

        DispatchQueue.main.async {
            guard let barra = self.criarBarraSeFaltar() else {
                call.reject("A tela do app ainda nao esta pronta.")
                return
            }

            var itens: [UITabBarItem] = []
            var ids: [String] = []
            var acoes: Set<String> = []
            for aba in abas {
                guard let id = aba["id"] as? String else { continue }
                let titulo = aba["titulo"] as? String
                let simbolo = (aba["simbolo"] as? String).flatMap { UIImage(systemName: $0) }
                let item = UITabBarItem(title: titulo, image: simbolo, tag: ids.count)
                item.accessibilityLabel = titulo
                itens.append(item)
                ids.append(id)
                if (aba["acao"] as? Bool) == true {
                    acoes.insert(id)
                }
            }
            self.ids = ids
            self.acoes = acoes

            if let clara = corClara.flatMap(UIColor.init(hex:)) {
                let escura = corEscura.flatMap(UIColor.init(hex:)) ?? clara
                barra.tintColor = UIColor { $0.userInterfaceStyle == .dark ? escura : clara }
            }

            barra.setItems(itens, animated: false)
            self.marcar(selecionada, em: barra)
            barra.isHidden = !self.deveMostrar
            self.avisarAltura()
            call.resolve()
        }
    }

    @objc func selecionar(_ call: CAPPluginCall) {
        let id = call.getString("id")
        DispatchQueue.main.async {
            if let barra = self.barra {
                self.marcar(id, em: barra)
            }
            call.resolve()
        }
    }

    @objc func mostrar(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            self.deveMostrar = true
            self.barra?.isHidden = false
            self.avisarAltura()
            call.resolve()
        }
    }

    @objc func esconder(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            self.deveMostrar = false
            self.barra?.isHidden = true
            self.avisarAltura()
            call.resolve()
        }
    }

    // MARK: - UITabBarDelegate

    public func tabBar(_ tabBar: UITabBar, didSelect item: UITabBarItem) {
        guard ids.indices.contains(item.tag) else { return }
        let id = ids[item.tag]
        if acoes.contains(id) {
            // O "+" so dispara a acao; a marcacao volta para a aba da tela atual.
            tabBar.selectedItem = anterior
        } else {
            anterior = item
        }
        notifyListeners("abaTocada", data: ["id": id])
    }

    /** Ultima aba marcada que nao e acao, para devolver a marcacao depois do "+". */
    private var anterior: UITabBarItem?

    // MARK: - Montagem

    private func marcar(_ id: String?, em barra: UITabBar) {
        let item = id.flatMap { ids.firstIndex(of: $0) }.flatMap { indice in
            barra.items?.first { $0.tag == indice }
        }
        barra.selectedItem = item
        anterior = item
    }

    /** Presa embaixo da tela: o UITabBar estende o fundo ate a borda e ajusta os itens a area segura. */
    private func criarBarraSeFaltar() -> BarraDeAbas? {
        if let barra = barra {
            return barra
        }
        guard let tela = bridge?.viewController?.view else {
            return nil
        }
        let barra = BarraDeAbas()
        barra.delegate = self
        barra.translatesAutoresizingMaskIntoConstraints = false
        barra.aoMudarDeTamanho = { [weak self] in self?.avisarAltura() }
        tela.addSubview(barra)

        // Altura propria da barra (49 pt ate o iOS 18; o iOS 26 define a sua).
        let alturaDaBarra = UITabBar().sizeThatFits(CGSize(width: tela.bounds.width, height: 0)).height
        NSLayoutConstraint.activate([
            barra.leadingAnchor.constraint(equalTo: tela.leadingAnchor),
            barra.trailingAnchor.constraint(equalTo: tela.trailingAnchor),
            barra.bottomAnchor.constraint(equalTo: tela.bottomAnchor),
            barra.topAnchor.constraint(
                equalTo: tela.safeAreaLayoutGuide.bottomAnchor,
                constant: -max(alturaDaBarra, 49)
            ),
        ])
        self.barra = barra
        return barra
    }

    /** Altura ocupada a partir da borda de baixo (inclui a barra de gestos); 0 com a barra escondida. */
    private func avisarAltura() {
        guard let barra = barra else { return }
        let altura = barra.isHidden ? 0 : barra.bounds.height
        guard altura != ultimaAltura else { return }
        ultimaAltura = altura
        notifyListeners("alturaMudou", data: ["altura": Double(altura)])
    }
}

/** UITabBar que avisa quando muda de tamanho (rotacao, troca de area segura). */
final class BarraDeAbas: UITabBar {
    var aoMudarDeTamanho: (() -> Void)?

    override func layoutSubviews() {
        super.layoutSubviews()
        aoMudarDeTamanho?()
    }
}

private extension UIColor {
    /** Aceita "#8e2f58" ou "8e2f58". */
    convenience init?(hex: String) {
        let texto = hex.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "#", with: "")
        guard texto.count == 6, let valor = UInt32(texto, radix: 16) else {
            return nil
        }
        self.init(
            red: CGFloat((valor >> 16) & 0xFF) / 255,
            green: CGFloat((valor >> 8) & 0xFF) / 255,
            blue: CGFloat(valor & 0xFF) / 255,
            alpha: 1
        )
    }
}
