import UIKit
import Capacitor

class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }

        window = UIWindow(windowScene: windowScene)
        window?.rootViewController = ViewControllerDoApp()
        window?.makeKeyAndVisible()

        SceneDelegateProxy.shared.scene(scene, willConnectTo: session, options: connectionOptions)

        // App aberto pelo atalho do icone ou por um link piggu://, ainda fechado.
        if let atalho = connectionOptions.shortcutItem {
            abrir(atalho)
        }
        connectionOptions.urlContexts.forEach { _ = AtalhosPlugin.abrir(url: $0.url) }
    }

    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        let dosAtalhos = URLContexts.filter { AtalhosPlugin.abrir(url: $0.url) }
        SceneDelegateProxy.shared.scene(scene, openURLContexts: URLContexts.subtracting(dosAtalhos))
    }

    /** Toque longo no icone com o app ja aberto. */
    func windowScene(_ windowScene: UIWindowScene, performActionFor shortcutItem: UIApplicationShortcutItem,
                     completionHandler: @escaping (Bool) -> Void) {
        completionHandler(abrir(shortcutItem))
    }

    @discardableResult
    private func abrir(_ atalho: UIApplicationShortcutItem) -> Bool {
        guard atalho.type == AtalhosPlugin.atalhoDeGasto else { return false }
        AtalhosPlugin.abrir(AtalhosPlugin.rotaDeGasto)
        return true
    }

    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        SceneDelegateProxy.shared.scene(scene, continue: userActivity)
    }
}
