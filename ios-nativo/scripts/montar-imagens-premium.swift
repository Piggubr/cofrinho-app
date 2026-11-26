// Imagem promocional de cada assinatura no App Store Connect: 1024 x 1024, sem alfa, sem print
// de tela e sem preco (a Apple mostra o preco ao lado). O porquinho, quem o plano cobre e o periodo.
// Uso: swift scripts/montar-imagens-premium.swift <pasta de saida>
import AppKit
import CoreText

let saida = URL(fileURLWithPath: CommandLine.arguments[1])
let raiz = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
if let dados = try? Data(contentsOf: raiz.appendingPathComponent("Piggu/Recursos/Sora.woff2")),
   let descritores = CTFontManagerCreateFontDescriptorsFromData(dados as CFData) as? [CTFontDescriptor],
   let primeiro = descritores.first {
    CTFontManagerRegisterFontDescriptors([primeiro] as CFArray, .process, true, nil)
}
func fonte(_ tamanho: CGFloat, _ peso: NSFont.Weight) -> NSFont {
    guard let sora = NSFont(name: "Sora", size: tamanho) else { return .systemFont(ofSize: tamanho, weight: peso) }
    return NSFont(descriptor: sora.fontDescriptor.addingAttributes([.traits: [NSFontDescriptor.TraitKey.weight: peso]]), size: tamanho) ?? sora
}
let rosa = NSColor(srgbRed: 0xD1 / 255, green: 0, blue: 0x6B / 255, alpha: 1)
let rosaClaro = NSColor(srgbRed: 1, green: 0x5C / 255, blue: 0xAE / 255, alpha: 1)
let porquinho = NSImage(contentsOf: raiz.appendingPathComponent("../frontend/public/ilustracoes/piggu-love.svg"))!

func texto(_ s: String, _ f: NSFont, y: CGFloat) {
    let p = NSMutableParagraphStyle(); p.alignment = .center
    (s as NSString).draw(in: NSRect(x: 0, y: y, width: 1024, height: f.pointSize * 1.3),
                         withAttributes: [.font: f, .foregroundColor: NSColor.white, .paragraphStyle: p])
}

let planos = [("individual", "Individual", "person.fill"), ("casal", "Casal", "person.2.fill"), ("familia", "Família", "person.3.fill")]
try FileManager.default.createDirectory(at: saida, withIntermediateDirectories: true)
for (chave, nome, simbolo) in planos {
    for (periodo, rotulo) in [("mensal", "Mensal"), ("anual", "Anual")] {
        let contexto = CGContext(data: nil, width: 1024, height: 1024, bitsPerComponent: 8, bytesPerRow: 0,
                                 space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
        NSGraphicsContext.saveGraphicsState()
        NSGraphicsContext.current = NSGraphicsContext(cgContext: contexto, flipped: false)
        NSGradient(starting: rosa, ending: rosaClaro)!.draw(in: NSRect(x: 0, y: 0, width: 1024, height: 1024), angle: -45)
        NSColor.white.withAlphaComponent(0.1).setFill()
        NSBezierPath(ovalIn: NSRect(x: 212, y: 400, width: 600, height: 600)).fill()
        porquinho.draw(in: NSRect(x: 292, y: 470, width: 440, height: 440 * 381.39 / 445.46))
        let config = NSImage.SymbolConfiguration(pointSize: 90, weight: .semibold).applying(.init(paletteColors: [.white]))
        if let icone = NSImage(systemSymbolName: simbolo, accessibilityDescription: nil)?.withSymbolConfiguration(config) {
            icone.draw(in: NSRect(x: 512 - icone.size.width / 2, y: 330, width: icone.size.width, height: icone.size.height))
        }
        texto("Premium \(nome)", fonte(96, .bold), y: 175)
        texto(rotulo, fonte(64, .medium), y: 85)
        NSGraphicsContext.restoreGraphicsState()
        try NSBitmapImageRep(cgImage: contexto.makeImage()!).representation(using: .png, properties: [:])!
            .write(to: saida.appendingPathComponent("promo-\(chave)-\(periodo).png"))
    }
}
print("pronto: \(saida.path)")
