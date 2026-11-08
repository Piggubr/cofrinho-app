package com.piggu.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.piggu.common.error.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class TextoDoPdfTest {

    private static final String TEXTO = "Total da fatura R$ 10,00";

    private static byte[] pdf(String texto, String senha) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage();
            doc.addPage(pagina);
            if (texto != null) {
                try (PDPageContentStream c = new PDPageContentStream(doc, pagina)) {
                    c.beginText();
                    c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    c.newLineAtOffset(50, 700);
                    c.showText(texto);
                    c.endText();
                }
            }
            if (senha != null) {
                StandardProtectionPolicy politica = new StandardProtectionPolicy(senha, senha, new AccessPermission());
                politica.setEncryptionKeyLength(128);
                doc.protect(politica);
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
        }
    }

    @Test
    void extraiOTexto() throws IOException {
        assertThat(TextoDoPdf.extrair(pdf(TEXTO, null), null)).contains(TEXTO);
    }

    @Test
    void comSenhaSemSenhaPede() throws IOException {
        byte[] cifrado = pdf(TEXTO, "1234");
        assertThatThrownBy(() -> TextoDoPdf.extrair(cifrado, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("PDF_COM_SENHA");
                    assertThat(e.getMessage()).isEqualTo("Este PDF tem senha. Digite a senha da fatura.");
                });
    }

    @Test
    void comSenhaCerta() throws IOException {
        assertThat(TextoDoPdf.extrair(pdf(TEXTO, "1234"), "1234")).contains(TEXTO);
    }

    @Test
    void senhaErrada() throws IOException {
        byte[] cifrado = pdf(TEXTO, "1234");
        assertThatThrownBy(() -> TextoDoPdf.extrair(cifrado, "0000"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("PDF_COM_SENHA");
                    assertThat(e.getMessage()).isEqualTo("A senha do PDF não confere.");
                });
    }

    @Test
    void naoEPdf() {
        assertThatThrownBy(() -> TextoDoPdf.extrair("oi".getBytes(), null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo("O arquivo não parece um PDF.");
                    assertThat(e.getCodigo()).isNotEqualTo("PDF_COM_SENHA");
                });
    }

    @Test
    void soImagemDaVazio() throws IOException {
        assertThat(TextoDoPdf.extrair(pdf(null, null), null).trim()).isEmpty();
    }

    @Test
    void ignoraPaginasAlemDoLimite() throws IOException {
        byte[] pdf;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            for (int i = 1; i <= 45; i++) {
                PDPage pagina = new PDPage();
                doc.addPage(pagina);
                try (PDPageContentStream c = new PDPageContentStream(doc, pagina)) {
                    c.beginText();
                    c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    c.newLineAtOffset(50, 700);
                    c.showText(i == 45 ? "MARCADOR45" : "pagina " + i);
                    c.endText();
                }
            }
            doc.save(saida);
            pdf = saida.toByteArray();
        }
        assertThat(TextoDoPdf.extrair(pdf, null)).contains("pagina 40").doesNotContain("MARCADOR45");
    }

    @Test
    void textoGrandeDemaisRecusa() throws IOException {
        byte[] pdf;
        String linha = "A".repeat(260);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            for (int p = 0; p < 20; p++) {
                PDPage pagina = new PDPage();
                doc.addPage(pagina);
                try (PDPageContentStream c = new PDPageContentStream(doc, pagina)) {
                    c.beginText();
                    c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 2);
                    c.newLineAtOffset(10, 780);
                    for (int l = 0; l < 100; l++) {
                        c.showText(linha);
                        c.newLineAtOffset(0, -3);
                    }
                    c.endText();
                }
            }
            doc.save(saida);
            pdf = saida.toByteArray();
        }
        assertThatThrownBy(() -> TextoDoPdf.extrair(pdf, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("Este PDF é grande demais para ler."));
    }
}
