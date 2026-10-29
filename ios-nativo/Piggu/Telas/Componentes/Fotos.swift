import PhotosUI
import SwiftUI
import UIKit

/// Galeria (PhotosPicker) e camera, lado a lado. A camera so aparece onde existe (no simulador, nao).
struct BotoesDeFoto: View {
    var tituloDaGaleria: LocalizedStringKey = "Escolher da galeria"
    let aoEscolher: (UIImage) -> Void

    @State private var item: PhotosPickerItem?
    @State private var usandoCamera = false

    private var temCamera: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }

    var body: some View {
        LadoALado {
            PhotosPicker(selection: $item, matching: .images, photoLibrary: .shared()) {
                Label(tituloDaGaleria, systemImage: "photo.on.rectangle")
            }
            if temCamera {
                Button { usandoCamera = true } label: { Label("Tirar foto", systemImage: "camera") }
            }
        }
        .buttonStyle(.bordered)
        .controlSize(.small)
        .onChange(of: item) { _, novo in
            guard let novo else { return }
            Task {
                if let dados = try? await novo.loadTransferable(type: Data.self), let imagem = UIImage(data: dados) {
                    aoEscolher(imagem)
                }
                item = nil
            }
        }
        .fullScreenCover(isPresented: $usandoCamera) {
            Camera { aoEscolher($0) }.ignoresSafeArea()
        }
    }
}

/// A camera do sistema (UIImagePickerController): o SwiftUI ainda nao tem uma propria.
struct Camera: UIViewControllerRepresentable {
    let aoTirar: (UIImage) -> Void

    @Environment(\.dismiss) private var fechar

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let camera = UIImagePickerController()
        camera.sourceType = .camera
        camera.delegate = context.coordinator
        return camera
    }

    func updateUIViewController(_: UIImagePickerController, context _: Context) {}

    func makeCoordinator() -> Coordenador { Coordenador(self) }

    final class Coordenador: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let camera: Camera

        init(_ camera: Camera) {
            self.camera = camera
        }

        func imagePickerController(_: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let imagem = info[.originalImage] as? UIImage {
                camera.aoTirar(imagem)
            }
            camera.fechar()
        }

        func imagePickerControllerDidCancel(_: UIImagePickerController) {
            camera.fechar()
        }
    }
}

/// Foto guardada no piggu-media, buscada com o token (o AsyncImage nao manda o Authorization).
struct FotoDoAsset: View {
    let assetId: String

    @Environment(Sessao.self) private var sessao
    @Environment(\.paleta) private var paleta
    @State private var imagem: UIImage?

    var body: some View {
        Group {
            if let imagem {
                Image(uiImage: imagem).resizable().scaledToFill()
            } else {
                paleta.suave
            }
        }
        .task(id: assetId) {
            if let dados = try? await sessao.cliente.imagem(assetId) { imagem = UIImage(data: dados) }
        }
        .accessibilityHidden(true)
    }
}

/// Foto da pessoa: a propria (data URL), a do Google (URL) ou a inicial do nome.
struct FotoDaPessoa: View {
    let foto: String?
    let nome: String
    var lado: CGFloat = 72

    @Environment(\.paleta) private var paleta

    var body: some View {
        Group {
            if let foto, let imagem = Imagem.deDataURL(foto) {
                Image(uiImage: imagem).resizable().scaledToFill()
            } else if let foto, let url = URL(string: foto), url.scheme?.hasPrefix("http") == true {
                AsyncImage(url: url) { $0.resizable().scaledToFill() } placeholder: { inicial }
            } else {
                inicial
            }
        }
        .frame(width: lado, height: lado)
        .clipShape(Circle())
        .accessibilityHidden(true)
    }

    private var inicial: some View {
        Text(nome.trimmingCharacters(in: .whitespaces).prefix(1).uppercased())
            .font(.sora(.title2, peso: .semibold))
            .foregroundStyle(paleta.primariaContraste)
            .frame(width: lado, height: lado)
            .background(paleta.primaria)
    }
}
