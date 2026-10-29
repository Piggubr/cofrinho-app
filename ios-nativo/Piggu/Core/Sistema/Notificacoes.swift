import UIKit
import UserNotifications

/// Notificacao no iPhone para os avisos do dia (fatura, orcamento, conta do mes), como o
/// AvisosNoCelular do site: pede a permissao quando a pessoa liga o aviso no Perfil e, a cada
/// abertura, renova o registro, porque o token muda quando o sistema quer.
@MainActor
enum Notificacoes {
    static let chaveDoToken = "piggu_token_do_aparelho"

    /// - Returns: se a pessoa deu a permissao.
    static func ativar() async -> Bool {
        let central = UNUserNotificationCenter.current()
        guard (try? await central.requestAuthorization(options: [.alert, .sound, .badge])) == true else { return false }
        UIApplication.shared.registerForRemoteNotifications()
        return true
    }

    /// Na abertura: so renova se a permissao ja foi dada, sem perguntar de novo.
    static func renovar() async {
        let situacao = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        if situacao == .authorized || situacao == .provisional {
            UIApplication.shared.registerForRemoteNotifications()
        }
    }

    /// O token em hexadecimal, como o backend guarda para o APNs.
    static func hexadecimal(_ token: Data) -> String {
        token.map { String(format: "%02x", $0) }.joined()
    }

    /// O link que a notificacao abre: o `rota` do aviso, se vier; senao o Painel, onde ficam os
    /// avisos do dia ("Para ficar de olho").
    static func destino(_ rota: String?) -> URL {
        rota.flatMap(URL.init(string:)).flatMap { Rota(url: $0) != nil ? $0 : nil } ?? URL(string: "piggu://painel")!
    }

    /// Ao sair, com a sessao ainda valendo; falhar aqui nao impede de sair.
    static func esquecerAparelho(_ cliente: ClienteDaAPI) async {
        guard let token = UserDefaults.standard.string(forKey: chaveDoToken) else { return }
        try? await cliente.delete("/auth/me/devices/\(token)")
        UserDefaults.standard.removeObject(forKey: chaveDoToken)
    }
}

/// Recebe o token do APNs e o toque na notificacao (o SwiftUI ainda nao tem como, sem um delegate).
final class DelegadoDoApp: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_: UIApplication, didFinishLaunchingWithOptions _: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken token: Data) {
        let texto = Notificacoes.hexadecimal(token)
        Task { @MainActor in
            UserDefaults.standard.set(texto, forKey: Notificacoes.chaveDoToken)
            // Sem rede agora: a proxima abertura do app registra de novo.
            let _: SemCorpo? = try? await Sessao.atual?.cliente.post("/auth/me/devices",
                                                                       corpo: ["token": texto, "plataforma": "IOS"])
        }
    }

    nonisolated func userNotificationCenter(_: UNUserNotificationCenter, willPresent _: UNNotification) async
        -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }

    nonisolated func userNotificationCenter(_: UNUserNotificationCenter, didReceive resposta: UNNotificationResponse) async {
        let rota = resposta.notification.request.content.userInfo["rota"] as? String
        await MainActor.run { CentralDeRotas.compartilhada.abrir(Notificacoes.destino(rota)) }
    }
}
