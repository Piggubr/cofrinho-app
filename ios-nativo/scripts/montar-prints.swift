// Monta os prints da App Store: fundo na cor do Piggu, uma frase curta no topo e a tela do app
// no centro, no mesmo tamanho em pixels da captura (o que a App Store pede para cada aparelho).
// Regras seguidas: developer.apple.com/app-store/asset-best-practices (frase que acrescenta,
// foco no centro, sem preço, sem selo, sem marca de terceiros).
// Uso: swift scripts/montar-prints.swift <pasta das capturas> <pasta de saida>
import AppKit
import CoreText

let legendas: [(tela: String, frase: String)] = [
    ("painel", "Saiba quanto ainda dá para gastar"),
    ("gastos", "Cada gasto no lugar certo"),
    ("receitas", "Receitas e quem te deve, juntos"),
    ("relatorios", "Veja para onde vai seu dinheiro"),
    ("orcamentos", "Limites que avisam antes"),
    ("metas", "Metas do mês que dão gosto"),
    ("calendario", "Seu mês, dia a dia"),
    ("familia", "As finanças da família juntas"),
]

let argumentos = CommandLine.arguments
guard argumentos.count == 3 else { fatalError("Uso: montar-prints.swift <capturas> <saida>") }
let entrada = URL(fileURLWithPath: argumentos[1])
let saida = URL(fileURLWithPath: argumentos[2])
let raiz = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()

// A Sora do app; sem ela, a fonte arredondada do sistema.
let sora = raiz.appendingPathComponent("Piggu/Recursos/Sora.woff2")
if let dados = try? Data(contentsOf: sora),
   let descritores = CTFontManagerCreateFontDescriptorsFromData(dados as CFData) as? [CTFontDescriptor],
   let primeiro = descritores.first {
    CTFontManagerRegisterFontDescriptors([primeiro] as CFArray, .process, true, nil)
}
func fonte(_ tamanho: CGFloat) -> NSFont {
    if let sora = NSFont(name: "Sora", size: tamanho) {
        let negrito = sora.fontDescriptor.addingAttributes([
            .traits: [NSFontDescriptor.TraitKey.weight: NSFont.Weight.bold],
        ])
        return NSFont(descriptor: negrito, size: tamanho) ?? sora
    }
    let sistema = NSFont.systemFont(ofSize: tamanho, weight: .bold)
    return NSFont(descriptor: sistema.fontDescriptor.withDesign(.rounded) ?? sistema.fontDescriptor, size: tamanho) ?? sistema
}

let rosa = NSColor(srgbRed: 0xD1 / 255, green: 0x00, blue: 0x6B / 255, alpha: 1)
let rosaClaro = NSColor(srgbRed: 1, green: 0x5C / 255, blue: 0xAE / 255, alpha: 1)

func montar(_ captura: NSBitmapImageRep, frase: String) -> Data {
    let largura = captura.pixelsWide, altura = captura.pixelsHigh
    let w = CGFloat(largura), h = CGFloat(altura)
    // RGBX: a App Store recusa PNG com canal alfa.
    let contexto = CGContext(data: nil, width: largura, height: altura, bitsPerComponent: 8, bytesPerRow: 0,
                             space: CGColorSpace(name: CGColorSpace.sRGB)!,
                             bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(cgContext: contexto, flipped: false)
    NSGradient(starting: rosa, ending: rosaClaro)!.draw(in: NSRect(x: 0, y: 0, width: w, height: h), angle: -90)

    // Frase centralizada, em ate duas linhas.
    let margem = w * 0.08
    let paragrafo = NSMutableParagraphStyle()
    paragrafo.alignment = .center
    paragrafo.lineHeightMultiple = 1.05
    let atributos: [NSAttributedString.Key: Any] = [
        .font: fonte(min(w * 0.085, h * 0.042)), .foregroundColor: NSColor.white, .paragraphStyle: paragrafo,
    ]
    let topo = h * 0.05, alturaDaFrase = h * 0.15

    // A tela do app, reduzida para caber abaixo da frase, com cantos e sombra.
    let espaco = NSRect(x: margem, y: h * 0.04, width: w - 2 * margem, height: h - topo - alturaDaFrase - h * 0.06)
    let escala = min(espaco.width / w, espaco.height / h)
    let quadro = NSRect(x: (w - w * escala) / 2, y: espaco.minY + (espaco.height - h * escala) / 2,
                        width: w * escala, height: h * escala)
    let raio = min(quadro.width, quadro.height) * 0.045
    let sombra = NSShadow()
    sombra.shadowColor = NSColor.black.withAlphaComponent(0.3)
    sombra.shadowBlurRadius = w * 0.03
    sombra.shadowOffset = NSSize(width: 0, height: -w * 0.01)
    NSGraphicsContext.saveGraphicsState()
    sombra.set()
    NSColor.white.setFill()
    NSBezierPath(roundedRect: quadro, xRadius: raio, yRadius: raio).fill()
    NSGraphicsContext.restoreGraphicsState()
    NSGraphicsContext.saveGraphicsState()
    NSBezierPath(roundedRect: quadro, xRadius: raio, yRadius: raio).addClip()
    captura.draw(in: quadro)
    NSGraphicsContext.restoreGraphicsState()
    // A frase no meio do espaco acima da tela.
    let caixa = NSSize(width: w - 2 * margem, height: alturaDaFrase)
    let ocupa = (frase as NSString).boundingRect(with: caixa, options: [.usesLineFragmentOrigin], attributes: atributos).height
    let y = quadro.maxY + (h - quadro.maxY - ocupa) / 2
    (frase as NSString).draw(with: NSRect(x: margem, y: y, width: caixa.width, height: ocupa),
                             options: [.usesLineFragmentOrigin], attributes: atributos)
    NSGraphicsContext.restoreGraphicsState()
    return NSBitmapImageRep(cgImage: contexto.makeImage()!).representation(using: .png, properties: [:])!
}

let aparelhos = try FileManager.default.contentsOfDirectory(at: entrada, includingPropertiesForKeys: nil)
    .filter { $0.hasDirectoryPath }
for aparelho in aparelhos {
    let destino = saida.appendingPathComponent(aparelho.lastPathComponent)
    try FileManager.default.createDirectory(at: destino, withIntermediateDirectories: true)
    for (ordem, item) in legendas.enumerated() {
        let arquivo = aparelho.appendingPathComponent("\(item.tela).png")
        guard let dados = try? Data(contentsOf: arquivo), let captura = NSBitmapImageRep(data: dados) else { continue }
        let png = montar(captura, frase: item.frase)
        try png.write(to: destino.appendingPathComponent(String(format: "%02d-%@.png", ordem + 1, item.tela)))
    }
    print("\(aparelho.lastPathComponent): pronto")
}
