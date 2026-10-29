import Foundation
import Security

/// Onde o app guarda o refresh token quando o recebe no corpo (D-058, D-072).
protocol CofreDoRefresh: Sendable {
    func ler() -> String?
    func guardar(_ refresh: String)
    func apagar()
}

/// Keychain, so neste aparelho e so depois do primeiro desbloqueio: nao vai para backup nem
/// para outro iPhone, e o app consegue renovar a sessao em segundo plano.
struct Keychain: CofreDoRefresh {
    var servico = "br.com.piggu.app.sessao"
    var conta = "refresh"

    private var consulta: [CFString: Any] {
        [kSecClass: kSecClassGenericPassword, kSecAttrService: servico, kSecAttrAccount: conta]
    }

    func ler() -> String? {
        var pedido = consulta
        pedido[kSecReturnData] = true
        pedido[kSecMatchLimit] = kSecMatchLimitOne
        var resultado: CFTypeRef?
        guard SecItemCopyMatching(pedido as CFDictionary, &resultado) == errSecSuccess,
              let dados = resultado as? Data else {
            return nil
        }
        return String(data: dados, encoding: .utf8)
    }

    func guardar(_ refresh: String) {
        let dados = Data(refresh.utf8)
        let atualizacao: [CFString: Any] = [
            kSecValueData: dados,
            kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        if SecItemUpdate(consulta as CFDictionary, atualizacao as CFDictionary) == errSecItemNotFound {
            SecItemAdd(consulta.merging(atualizacao) { $1 } as CFDictionary, nil)
        }
    }

    func apagar() {
        SecItemDelete(consulta as CFDictionary)
    }
}
