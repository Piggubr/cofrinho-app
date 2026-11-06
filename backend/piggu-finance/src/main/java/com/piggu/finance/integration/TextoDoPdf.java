package com.piggu.finance.integration;

import com.piggu.common.error.BusinessException;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;

/**
 * Extrai o texto de um PDF (fatura de cartao) para o {@link LeitorDeFatura}. O PDF e entrada
 * nao confiavel: o documento e sempre fechado e as streams grandes vao para arquivo
 * temporario (apagado ao fechar) em vez de ocupar o heap. O conteudo nunca e logado.
 */
public final class TextoDoPdf {

    public static final String CODIGO_SENHA = "PDF_COM_SENHA";

    private TextoDoPdf() {
    }

    /** @param senha senha do PDF; pode ser nula. PDF so com imagem devolve texto vazio. */
    public static String extrair(byte[] pdf, String senha) {
        try (PDDocument doc = Loader.loadPDF(pdf, senha == null ? "" : senha, null, null,
                IOUtils.createTempFileOnlyStreamCache())) {
            PDFTextStripper leitor = new PDFTextStripper();
            leitor.setSortByPosition(true);
            return leitor.getText(doc);
        } catch (InvalidPasswordException e) {
            throw new BusinessException(senha == null || senha.isEmpty()
                    ? "Este PDF tem senha. Digite a senha da fatura." : "A senha do PDF não confere.",
                    HttpStatus.UNPROCESSABLE_CONTENT, CODIGO_SENHA);
        } catch (IOException | RuntimeException e) {
            throw new BusinessException("O arquivo não parece um PDF.");
        }
    }
}
