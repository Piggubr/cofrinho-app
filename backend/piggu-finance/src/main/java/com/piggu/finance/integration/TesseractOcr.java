package com.piggu.finance.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * OCR no proprio servidor, chamando o Tesseract instalado na imagem (apk tesseract-ocr).
 *
 * <p>Pela linha de comando, sem biblioteca Java: o binario ja trata JPEG, PNG e WebP e
 * binariza a foto sozinho. A foto nao sai do servidor.</p>
 *
 * <p>Sem o Tesseract instalado (ex.: rodando fora do Docker) devolve vazio e a leitura
 * segue para o Gemini.</p>
 */
@Component
public class TesseractOcr {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcr.class);

    private final String comando;
    private final Duration limite;

    public TesseractOcr(@Value("${piggu.integracoes.ocr.comando:tesseract}") String comando,
                        @Value("${piggu.integracoes.ocr.timeout:20s}") Duration limite) {
        this.comando = comando;
        this.limite = limite;
    }

    /** Texto da foto, ou vazio quando o OCR nao esta disponivel ou falhou. */
    public Optional<String> ler(byte[] imagem) {
        if (comando == null || comando.isBlank()) {
            return Optional.empty();
        }
        Path arquivo = null;
        Path texto = null;
        try {
            arquivo = Files.createTempFile("recibo-", ".img");
            texto = Files.createTempFile("recibo-", ".txt");
            Files.write(arquivo, imagem);
            // psm 4: uma coluna de linhas de tamanhos variados, o formato de um cupom.
            // preserve_interword_spaces mantem a coluna do valor separada da descricao.
            Process processo = new ProcessBuilder(comando, arquivo.toString(), "stdout",
                    "-l", "por+eng", "--psm", "4", "-c", "preserve_interword_spaces=1")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    // Saida em arquivo, nao em pipe: ler o pipe travaria antes do limite de tempo.
                    .redirectOutput(texto.toFile())
                    .start();
            if (!processo.waitFor(limite.toMillis(), TimeUnit.MILLISECONDS)) {
                processo.destroyForcibly();
                log.warn("OCR passou de {} e foi interrompido", limite);
                return Optional.empty();
            }
            if (processo.exitValue() != 0) {
                log.warn("OCR terminou com codigo {}", processo.exitValue());
                return Optional.empty();
            }
            return Optional.of(Files.readString(texto, StandardCharsets.UTF_8));
        } catch (IOException erro) {
            log.info("OCR indisponivel ({}); a leitura segue para o Gemini", erro.getMessage());
            return Optional.empty();
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            apagar(arquivo);
            apagar(texto);
        }
    }

    private static void apagar(Path arquivo) {
        if (arquivo == null) {
            return;
        }
        try {
            // A foto e da pessoa: nao fica em disco depois da leitura.
            Files.deleteIfExists(arquivo);
        } catch (IOException erro) {
            log.warn("Nao foi possivel apagar o arquivo temporario do OCR", erro);
        }
    }
}
