import SwiftUI
import UIKit

/// View vazia que avisa quando entra numa janela: a ponte para o que so a janela do UIKit faz.
final class Ancora: UIView {
    var aoEntrarNaJanela: ((UIWindow?) -> Void)?

    override func didMoveToWindow() {
        super.didMoveToWindow()
        aoEntrarNaJanela?(window)
    }
}

/// Tocar fora de um campo fecha o teclado, em qualquer tela ou folha da janela. O SwiftUI nao
/// faz isso sozinho, e o teclado numerico do valor nao tem tecla para fechar.
struct FecharTecladoAoTocarFora: UIViewRepresentable {
    func makeCoordinator() -> Coordenador { Coordenador() }

    func makeUIView(context: Context) -> Ancora {
        let ancora = Ancora()
        ancora.isUserInteractionEnabled = false
        let coordenador = context.coordinator
        ancora.aoEntrarNaJanela = { coordenador.instalar(em: $0) }
        return ancora
    }

    func updateUIView(_ ancora: Ancora, context: Context) {}

    @MainActor
    final class Coordenador: NSObject, UIGestureRecognizerDelegate {
        private weak var janela: UIWindow?

        func instalar(em janela: UIWindow?) {
            guard let janela, janela !== self.janela else { return }
            self.janela = janela
            let toque = UITapGestureRecognizer(target: self, action: #selector(tocou))
            // O toque segue para o botao ou a linha tocada, sem atraso.
            toque.cancelsTouchesInView = false
            toque.delaysTouchesEnded = false
            toque.delegate = self
            janela.addGestureRecognizer(toque)
        }

        @objc private func tocou(_ toque: UITapGestureRecognizer) {
            toque.view?.endEditing(true)
        }

        /// Tocar em outro campo so troca o foco, sem fechar e reabrir o teclado.
        func gestureRecognizer(_ reconhecedor: UIGestureRecognizer, shouldReceive toque: UITouch) -> Bool {
            var view = toque.view
            while let atual = view {
                if atual is UITextField || atual is UITextView { return false }
                view = atual.superview
            }
            return true
        }

        func gestureRecognizer(_ reconhecedor: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith outro: UIGestureRecognizer) -> Bool {
            true
        }
    }
}

/// A trava numa janela propria, acima das folhas abertas (lancar gasto, leitor de nota), que um
/// overlay na raiz nao cobre. Ao travar, fecha o teclado, que ficaria por cima da trava.
struct JanelaDaTrava: UIViewRepresentable {
    let trava: TravaDoApp
    let ativa: Bool
    let paleta: Paleta
    let esquema: ColorScheme?

    func makeCoordinator() -> Coordenador { Coordenador() }

    func makeUIView(context: Context) -> Ancora {
        let ancora = Ancora()
        ancora.isUserInteractionEnabled = false
        return ancora
    }

    func updateUIView(_ ancora: Ancora, context: Context) {
        let coordenador = context.coordinator
        let aplicar = { [self] (janela: UIWindow?) in coordenador.aplicar(self, sobre: janela) }
        ancora.aoEntrarNaJanela = aplicar
        aplicar(ancora.window)
    }

    @MainActor
    final class Coordenador {
        private var janelaDaTrava: UIWindow?

        func aplicar(_ pedido: JanelaDaTrava, sobre janela: UIWindow?) {
            guard pedido.ativa else {
                janelaDaTrava?.isHidden = true
                janelaDaTrava = nil
                return
            }
            let tela = AnyView(TelaDeTrava(trava: pedido.trava)
                .environment(\.paleta, pedido.paleta)
                .tint(pedido.paleta.primaria)
                .font(.sora()))
            let estilo: UIUserInterfaceStyle = switch pedido.esquema {
            case .light: .light
            case .dark: .dark
            default: .unspecified
            }
            if let janelaDaTrava, let hospedeiro = janelaDaTrava.rootViewController as? UIHostingController<AnyView> {
                hospedeiro.rootView = tela
                janelaDaTrava.overrideUserInterfaceStyle = estilo
                return
            }
            guard let janela, let cena = janela.windowScene else { return }
            janela.endEditing(true)
            let nova = UIWindow(windowScene: cena)
            nova.windowLevel = .alert + 1
            nova.overrideUserInterfaceStyle = estilo
            let hospedeiro = UIHostingController(rootView: tela)
            hospedeiro.view.backgroundColor = .clear
            nova.rootViewController = hospedeiro
            nova.isHidden = false
            janelaDaTrava = nova
        }
    }
}
