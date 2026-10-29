import UIKit

/// Fotos que o app envia: recortadas e reduzidas no aparelho, como o site (core/ui/arquivo.ts).
enum Imagem {
    /// Lado da foto de perfil (LADO_DA_FOTO_DE_PERFIL do site).
    static let ladoDaFotoDePerfil: CGFloat = 320

    /// Quadrado do centro, no maximo 320 px, em JPEG sobre fundo branco.
    static func fotoDePerfil(_ imagem: UIImage) -> Data? {
        let tamanho = imagem.size
        let lado = min(tamanho.width, tamanho.height)
        guard lado > 0 else { return nil }
        let destino = min(ladoDaFotoDePerfil, lado * imagem.scale)
        let formato = UIGraphicsImageRendererFormat()
        formato.scale = 1
        formato.opaque = true
        let desenho = UIGraphicsImageRenderer(size: CGSize(width: destino, height: destino), format: formato)
        let escala = destino / lado
        return desenho.jpegData(withCompressionQuality: 0.85) { contexto in
            UIColor.white.setFill()
            contexto.fill(CGRect(x: 0, y: 0, width: destino, height: destino))
            imagem.draw(in: CGRect(x: -(tamanho.width - lado) / 2 * escala, y: -(tamanho.height - lado) / 2 * escala,
                                   width: tamanho.width * escala, height: tamanho.height * escala))
        }
    }

    /// Foto de produto (Girls Math): o lado maior em ate 1600 px, em JPEG, abaixo dos 5 MB do backend.
    static func fotoDeProduto(_ imagem: UIImage) -> Data? {
        let maior = max(imagem.size.width, imagem.size.height)
        let escala = min(1, 1600 / max(maior, 1))
        let tamanho = CGSize(width: imagem.size.width * escala, height: imagem.size.height * escala)
        let formato = UIGraphicsImageRendererFormat()
        formato.scale = 1
        return UIGraphicsImageRenderer(size: tamanho, format: formato).jpegData(withCompressionQuality: 0.8) { _ in
            imagem.draw(in: CGRect(origin: .zero, size: tamanho))
        }
    }

    /// A foto do perfil vem como URL (Google) ou data URL (a propria, enviada pelo app).
    static func deDataURL(_ texto: String) -> UIImage? {
        guard texto.hasPrefix("data:"), let virgula = texto.firstIndex(of: ","),
              let dados = Data(base64Encoded: String(texto[texto.index(after: virgula)...])) else { return nil }
        return UIImage(data: dados)
    }
}
