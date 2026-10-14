package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;

/**
 * Prepara a foto de perfil enviada pela pessoa.
 *
 * <p>O app ja manda a imagem reduzida, mas o servidor nao confia nisso: le so JPEG e PNG,
 * confere as dimensoes antes de abrir (uma PNG pequena pode descomprimir em gigabytes),
 * recorta um quadrado no centro e regrava em JPEG. Regravar descarta todo metadado, entao
 * a localizacao que o celular grava na foto nunca chega ao banco.</p>
 */
public final class FotoDePerfil {

    /** Lado do quadrado gravado; o maior avatar do app tem 64 px, 256 cobre telas 3x. */
    static final int LADO = 256;

    /**
     * Bytes do envio, ja decodificado do base64. O app manda um JPEG de 320 px, bem menor;
     * o teto cabe no limite de corpo das rotas comuns (256 KB em base64).
     */
    static final int BYTES_MAXIMOS = 160 * 1024;

    /** Maior lado aceito antes de abrir a imagem. */
    static final int DIMENSAO_MAXIMA = 6000;

    private static final String PREFIXO = "data:image/jpeg;base64,";

    private FotoDePerfil() {
    }

    /** @return a data URL do JPEG pronto para gravar */
    public static String preparar(String base64, String mimeType) {
        if (!"image/jpeg".equals(mimeType) && !"image/png".equals(mimeType)) {
            throw invalida("Envie a foto em JPEG ou PNG.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(base64 == null ? "" : base64);
        } catch (IllegalArgumentException erro) {
            throw invalida("Não consegui ler essa foto.");
        }
        if (bytes.length == 0) {
            throw invalida("Não consegui ler essa foto.");
        }
        if (bytes.length > BYTES_MAXIMOS) {
            throw invalida("Essa foto é grande demais. Escolha outra.");
        }
        BufferedImage original = ler(bytes);
        return PREFIXO + Base64.getEncoder().encodeToString(gravarJpeg(quadrado(original)));
    }

    private static BufferedImage ler(byte[] bytes) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            while (leitores.hasNext()) {
                ImageReader leitor = leitores.next();
                String formato = leitor.getFormatName().toLowerCase();
                if (!formato.equals("jpeg") && !formato.equals("png")) {
                    leitor.dispose();
                    continue;
                }
                try {
                    leitor.setInput(entrada, true, true);
                    int largura = leitor.getWidth(0);
                    int altura = leitor.getHeight(0);
                    if (largura <= 0 || altura <= 0 || largura > DIMENSAO_MAXIMA || altura > DIMENSAO_MAXIMA) {
                        throw invalida("Essa foto é grande demais. Escolha outra.");
                    }
                    // Le pulando pixels: o avatar sai com 256 px, e um PNG liso de 6000 px
                    // (poucos KB) aberto inteiro ocuparia centenas de MB de memoria.
                    ImageReadParam parametros = leitor.getDefaultReadParam();
                    int passo = Math.max(1, Math.min(largura, altura) / (LADO * 2));
                    parametros.setSourceSubsampling(passo, passo, 0, 0);
                    return leitor.read(0, parametros);
                } finally {
                    leitor.dispose();
                }
            }
        } catch (BusinessException grandeDemais) {
            throw grandeDemais;
        } catch (IOException | RuntimeException arquivoQuebrado) {
            // Cai no aviso de baixo: para a pessoa, e so uma foto que nao abriu.
        }
        throw invalida("Não consegui ler essa foto.");
    }

    private static BufferedImage quadrado(BufferedImage original) {
        int lado = Math.min(original.getWidth(), original.getHeight());
        int x = (original.getWidth() - lado) / 2;
        int y = (original.getHeight() - lado) / 2;
        int destino = Math.min(LADO, lado);
        BufferedImage saida = new BufferedImage(destino, destino, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = saida.createGraphics();
        try {
            // PNG transparente vira fundo branco em vez de preto.
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, destino, destino);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(original, 0, 0, destino, destino, x, y, x + lado, y + lado, null);
        } finally {
            g.dispose();
        }
        return saida;
    }

    private static byte[] gravarJpeg(BufferedImage imagem) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream destino = new MemoryCacheImageOutputStream(saida)) {
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(0.85f);
            escritor.setOutput(destino);
            escritor.write(null, new IIOImage(imagem, null, null), parametros);
        } catch (IOException erro) {
            throw new IllegalStateException("Falha ao gravar a foto de perfil", erro);
        } finally {
            escritor.dispose();
        }
        return saida.toByteArray();
    }

    private static BusinessException invalida(String mensagem) {
        return new BusinessException(mensagem);
    }
}
