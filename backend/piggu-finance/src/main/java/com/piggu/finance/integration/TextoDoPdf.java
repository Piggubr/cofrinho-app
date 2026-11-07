package com.piggu.finance.integration;

import com.piggu.common.error.BusinessException;
import java.io.IOException;
import java.io.Writer;
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

    // shortcut: le so as 40 primeiras paginas (fatura real tem poucas); subir se aparecer fatura maior.
    static final int MAX_PAGINAS = 40;
    // shortcut: corta em 500 mil caracteres (fatura real tem poucos milhares); subir se uma fatura de verdade passar disso.
    static final int MAX_CARACTERES = 500_000;

    private TextoDoPdf() {
    }

    /** @param senha senha do PDF; pode ser nula. PDF so com imagem devolve texto vazio. */
    public static String extrair(byte[] pdf, String senha) {
        try (PDDocument doc = Loader.loadPDF(pdf, senha == null ? "" : senha, null, null,
                IOUtils.createTempFileOnlyStreamCache())) {
            PDFTextStripper leitor = new PDFTextStripper();
            leitor.setSortByPosition(true);
            leitor.setEndPage(MAX_PAGINAS);
            Limitado texto = new Limitado();
            leitor.writeText(doc, texto);
            return texto.lido.toString();
        } catch (GrandeDemais e) {
            throw new BusinessException("Este PDF é grande demais para ler.");
        } catch (InvalidPasswordException e) {
            throw new BusinessException(senha == null || senha.isEmpty()
                    ? "Este PDF tem senha. Digite a senha da fatura." : "A senha do PDF não confere.",
                    HttpStatus.UNPROCESSABLE_CONTENT, CODIGO_SENHA);
        } catch (IOException | RuntimeException e) {
            throw new BusinessException("O arquivo não parece um PDF.");
        }
    }

    /** Guarda o texto e para a leitura ao passar de {@link #MAX_CARACTERES}. */
    private static final class Limitado extends Writer {
        private final StringBuilder lido = new StringBuilder();

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            if (lido.length() + len > MAX_CARACTERES) {
                throw new GrandeDemais();
            }
            lido.append(cbuf, off, len);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static final class GrandeDemais extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
