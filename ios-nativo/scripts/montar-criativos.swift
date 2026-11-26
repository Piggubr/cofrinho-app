// Artes da App Store no iOS 27: o cabecalho da pagina do produto (21:9, 3840 x 1646, area segura
// 1646 x 661 no centro) e o resultado de busca (3:2, 3840 x 2560, area segura 2168 x 1030 no
// centro), pelas creative assets specifications da Apple. O que importa fica na area segura; as
// telas do app em volta sao ambiente. Sem alfa, sem preco, sem marca de terceiros.
// Uso: swift scripts/montar-criativos.swift <capturas do iPhone> <pasta de saida>
// Grava tambem <saida>/guias, com a area segura marcada, so para conferir (nao suba).
import AppKit
import CoreText

let argumentos = CommandLine.arguments
guard argumentos.count == 3 else { fatalError("Uso: montar-criativos.swift <capturas> <saida>") }
let capturas = URL(fileURLWithPath: argumentos[1])
let saida = URL(fileURLWithPath: argumentos[2])
let raiz = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()

if let dados = try? Data(contentsOf: raiz.appendingPathComponent("Piggu/Recursos/Sora.woff2")),
   let descritores = CTFontManagerCreateFontDescriptorsFromData(dados as CFData) as? [CTFontDescriptor],
   let primeiro = descritores.first {
    CTFontManagerRegisterFontDescriptors([primeiro] as CFArray, .process, true, nil)
}
func fonte(_ tamanho: CGFloat) -> NSFont {
    guard let sora = NSFont(name: "Sora", size: tamanho) else { return .systemFont(ofSize: tamanho, weight: .bold) }
    let negrito = sora.fontDescriptor.addingAttributes([.traits: [NSFontDescriptor.TraitKey.weight: NSFont.Weight.bold]])
    return NSFont(descriptor: negrito, size: tamanho) ?? sora
}
let rosa = NSColor(srgbRed: 0xD1 / 255, green: 0, blue: 0x6B / 255, alpha: 1)
let rosaClaro = NSColor(srgbRed: 1, green: 0x5C / 255, blue: 0xAE / 255, alpha: 1)
let porquinho = NSImage(contentsOf: raiz.appendingPathComponent("../frontend/public/ilustracoes/piggu-love.svg"))!
func tela(_ nome: String) -> NSImage { NSImage(contentsOf: capturas.appendingPathComponent("\(nome).png"))! }

/// Desenha numa tela RGBX (sem alfa) com y para cima e devolve o PNG.
func desenhar(_ largura: Int, _ altura: Int, guia: NSRect?, _ corpo: (CGFloat, CGFloat) -> Void) -> Data {
    let contexto = CGContext(data: nil, width: largura, height: altura, bitsPerComponent: 8, bytesPerRow: 0,
                             space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(cgContext: contexto, flipped: false)
    let w = CGFloat(largura), h = CGFloat(altura)
    NSGradient(starting: rosa, ending: rosaClaro)!.draw(in: NSRect(x: 0, y: 0, width: w, height: h), angle: -35)
    // Bolhas claras ao fundo, so ambiente.
    for (x, y, r) in [(0.08, 0.85, 0.16), (0.93, 0.15, 0.2), (0.5, -0.05, 0.12), (0.72, 0.95, 0.09)] as [(CGFloat, CGFloat, CGFloat)] {
        NSColor.white.withAlphaComponent(0.08).setFill()
        NSBezierPath(ovalIn: NSRect(x: w * x - h * r, y: h * y - h * r, width: 2 * h * r, height: 2 * h * r)).fill()
    }
    corpo(w, h)
    if let guia {
        NSColor.yellow.setStroke()
        let caminho = NSBezierPath(rect: guia)
        caminho.lineWidth = 6
        caminho.setLineDash([30, 20], count: 2, phase: 0)
        caminho.stroke()
    }
    NSGraphicsContext.restoreGraphicsState()
    return NSBitmapImageRep(cgImage: contexto.makeImage()!).representation(using: .png, properties: [:])!
}

/// Uma tela do app com cantos arredondados, sombra e uma leve inclinacao.
func aparelho(_ imagem: NSImage, centro: NSPoint, altura: CGFloat, giro: CGFloat) {
    let largura = altura * imagem.size.width / imagem.size.height
    let quadro = NSRect(x: -largura / 2, y: -altura / 2, width: largura, height: altura)
    let raio = largura * 0.11
    NSGraphicsContext.saveGraphicsState()
    let transformacao = NSAffineTransform()
    transformacao.translateX(by: centro.x, yBy: centro.y)
    transformacao.rotate(byDegrees: giro)
    transformacao.concat()
    let sombra = NSShadow()
    sombra.shadowColor = NSColor.black.withAlphaComponent(0.35)
    sombra.shadowBlurRadius = altura * 0.04
    sombra.shadowOffset = NSSize(width: 0, height: -altura * 0.015)
    NSGraphicsContext.saveGraphicsState()
    sombra.set()
    NSColor(white: 0.1, alpha: 1).setFill()
    NSBezierPath(roundedRect: quadro.insetBy(dx: -largura * 0.025, dy: -largura * 0.025), xRadius: raio * 1.15, yRadius: raio * 1.15).fill()
    NSGraphicsContext.restoreGraphicsState()
    NSBezierPath(roundedRect: quadro, xRadius: raio, yRadius: raio).addClip()
    imagem.draw(in: quadro)
    NSGraphicsContext.restoreGraphicsState()
}

func frase(_ texto: String, em caixa: NSRect, tamanho: CGFloat, alinhamento: NSTextAlignment) {
    let paragrafo = NSMutableParagraphStyle()
    paragrafo.alignment = alinhamento
    paragrafo.lineHeightMultiple = 1.02
    let atributos: [NSAttributedString.Key: Any] = [.font: fonte(tamanho), .foregroundColor: NSColor.white, .paragraphStyle: paragrafo]
    let ocupa = (texto as NSString).boundingRect(with: caixa.size, options: [.usesLineFragmentOrigin], attributes: atributos).height
    (texto as NSString).draw(with: NSRect(x: caixa.minX, y: caixa.midY - ocupa / 2, width: caixa.width, height: ocupa),
                             options: [.usesLineFragmentOrigin], attributes: atributos)
}

// Cabecalho: uma ideia so no centro (o porquinho e a frase); as telas nas laterais sao ambiente.
let seguraDoCabecalho = NSRect(x: 1097, y: 1646 - 493 - 661, width: 1646, height: 661)
func cabecalho(guia: Bool) -> Data {
    desenhar(3840, 1646, guia: guia ? seguraDoCabecalho : nil) { w, h in
        aparelho(tela("relatorios"), centro: NSPoint(x: w * 0.1, y: h * 0.42), altura: h * 1.15, giro: 8)
        aparelho(tela("gastos"), centro: NSPoint(x: w * 0.9, y: h * 0.42), altura: h * 1.15, giro: -8)
        let s = seguraDoCabecalho
        let lado = s.height * 0.95
        porquinho.draw(in: NSRect(x: s.minX, y: s.midY - lado * 0.43, width: lado, height: lado * 381.39 / 445.46))
        frase("O dinheiro da família, sem susto", em: NSRect(x: s.minX + lado * 1.05, y: s.minY, width: s.width - lado * 1.05, height: s.height),
              tamanho: 118, alinhamento: .left)
    }
}

// Busca: o obvio e o app em uso. A frase a esquerda e o Painel a direita, com o "Ainda da para
// gastar" dentro da area segura.
let seguraDaBusca = NSRect(x: 836, y: 2560 - 765 - 1030, width: 2168, height: 1030)
func busca(guia: Bool) -> Data {
    desenhar(3840, 2560, guia: guia ? seguraDaBusca : nil) { w, h in
        let s = seguraDaBusca
        let altura = h * 0.98
        let largura = altura * 1320 / 2868
        let centroDoPainel = s.maxX - largura * 0.55
        aparelho(tela("gastos"), centro: NSPoint(x: centroDoPainel + largura * 0.62, y: s.midY - altura * 0.15), altura: altura * 0.9, giro: -7)
        aparelho(tela("painel"), centro: NSPoint(x: centroDoPainel, y: s.midY - altura * 0.2), altura: altura, giro: 0)
        let livre = centroDoPainel - largura / 2 - s.minX - 80
        frase("Controle os gastos da família", em: NSRect(x: s.minX, y: s.minY, width: livre, height: s.height),
              tamanho: 150, alinhamento: .left)
    }
}

try FileManager.default.createDirectory(at: saida.appendingPathComponent("guias"), withIntermediateDirectories: true)
try cabecalho(guia: false).write(to: saida.appendingPathComponent("cabecalho-3840x1646.png"))
try busca(guia: false).write(to: saida.appendingPathComponent("busca-3840x2560.png"))
try cabecalho(guia: true).write(to: saida.appendingPathComponent("guias/cabecalho-area-segura.png"))
try busca(guia: true).write(to: saida.appendingPathComponent("guias/busca-area-segura.png"))
print("pronto: \(saida.path)")
