package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FotoDePerfilTest {

    private static final String PREFIXO = "data:image/jpeg;base64,";

    @Test
    @DisplayName("foto retangular vira um quadrado de 256 px em JPEG")
    void recortaEReduz() throws IOException {
        String dataUrl = FotoDePerfil.preparar(base64(imagem(800, 600, Color.RED), "png"), "image/png");

        BufferedImage gravada = ler(dataUrl);
        assertThat(gravada.getWidth()).isEqualTo(FotoDePerfil.LADO);
        assertThat(gravada.getHeight()).isEqualTo(FotoDePerfil.LADO);
    }

    @Test
    @DisplayName("foto pequena nao e ampliada")
    void naoAmplia() throws IOException {
        String dataUrl = FotoDePerfil.preparar(base64(imagem(100, 120, Color.BLUE), "jpeg"), "image/jpeg");

        assertThat(ler(dataUrl).getWidth()).isEqualTo(100);
    }

    @Test
    @DisplayName("metadados da foto original (como a localizacao) nao sao gravados")
    void descartaMetadados() throws IOException {
        byte[] comComentario = jpegComComentario(imagem(300, 300, Color.GREEN), "GPS -23.5505 -46.6333");
        assertThat(new String(comComentario, StandardCharsets.ISO_8859_1)).contains("GPS -23.5505");

        String dataUrl = FotoDePerfil.preparar(Base64.getEncoder().encodeToString(comComentario), "image/jpeg");

        byte[] gravada = Base64.getDecoder().decode(dataUrl.substring(PREFIXO.length()));
        assertThat(new String(gravada, StandardCharsets.ISO_8859_1)).doesNotContain("GPS");
    }

    @Test
    @DisplayName("PNG transparente ganha fundo branco")
    void transparenteViraBranco() throws IOException {
        BufferedImage transparente = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);

        BufferedImage gravada = ler(FotoDePerfil.preparar(base64(transparente, "png"), "image/png"));

        Color centro = new Color(gravada.getRGB(25, 25));
        assertThat(centro.getRed()).isGreaterThan(240);
        assertThat(centro.getGreen()).isGreaterThan(240);
    }

    @Test
    @DisplayName("tipo fora de JPEG e PNG e recusado")
    void tipoRecusado() {
        assertThatThrownBy(() -> FotoDePerfil.preparar("AAAA", "image/svg+xml"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("bytes que nao sao imagem sao recusados, mesmo dizendo ser JPEG")
    void naoEImagem() {
        String texto = Base64.getEncoder().encodeToString("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> FotoDePerfil.preparar(texto, "image/jpeg"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Não consegui ler");
    }

    @Test
    @DisplayName("base64 quebrado e vazio sao recusados")
    void base64Invalido() {
        assertThatThrownBy(() -> FotoDePerfil.preparar("@@@", "image/png")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> FotoDePerfil.preparar("", "image/png")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> FotoDePerfil.preparar(null, "image/png")).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("envio acima do teto e recusado antes de abrir")
    void grandeDemais() {
        String enorme = Base64.getEncoder().encodeToString(new byte[FotoDePerfil.BYTES_MAXIMOS + 1]);

        assertThatThrownBy(() -> FotoDePerfil.preparar(enorme, "image/png"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("grande demais");
    }

    @Test
    @DisplayName("PNG pequena que abriria gigante e recusada pelas dimensoes")
    void dimensaoGigante() throws IOException {
        // Uma cor so comprime muito: o arquivo e pequeno, a imagem aberta nao seria.
        BufferedImage gigante = new BufferedImage(FotoDePerfil.DIMENSAO_MAXIMA + 1, 10, BufferedImage.TYPE_BYTE_BINARY);

        assertThatThrownBy(() -> FotoDePerfil.preparar(base64(gigante, "png"), "image/png"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("grande demais");
    }

    private static BufferedImage imagem(int largura, int altura, Color cor) {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        g.setColor(cor);
        g.fillRect(0, 0, largura, altura);
        g.dispose();
        return imagem;
    }

    private static String base64(BufferedImage imagem, String formato) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagem, formato, saida);
        return Base64.getEncoder().encodeToString(saida.toByteArray());
    }

    private static BufferedImage ler(String dataUrl) throws IOException {
        assertThat(dataUrl).startsWith(PREFIXO);
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(dataUrl.substring(PREFIXO.length()))));
    }

    /** JPEG com um comentario nos metadados, no papel do EXIF de um celular. */
    private static byte[] jpegComComentario(BufferedImage imagem, String comentario) throws IOException {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        IIOMetadata metadados = escritor.getDefaultImageMetadata(
                javax.imageio.ImageTypeSpecifier.createFromRenderedImage(imagem), null);
        IIOMetadataNode raiz = (IIOMetadataNode) metadados.getAsTree("javax_imageio_jpeg_image_1.0");
        IIOMetadataNode com = new IIOMetadataNode("com");
        com.setAttribute("comment", comentario);
        ((IIOMetadataNode) raiz.getElementsByTagName("markerSequence").item(0)).appendChild(com);
        metadados.setFromTree("javax_imageio_jpeg_image_1.0", raiz);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream destino = new MemoryCacheImageOutputStream(saida)) {
            escritor.setOutput(destino);
            escritor.write(new IIOImage(imagem, null, metadados));
        } finally {
            escritor.dispose();
        }
        return saida.toByteArray();
    }
}
