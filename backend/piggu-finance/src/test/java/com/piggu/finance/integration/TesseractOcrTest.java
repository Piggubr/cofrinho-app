package com.piggu.finance.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O OCR chama o binario do Tesseract; aqui um script no lugar dele. A foto vai num arquivo
 * temporario que so o dono le e que some depois da leitura, de qualquer jeito que ela acabe.
 */
@EnabledOnOs({OS.LINUX, OS.MAC})
class TesseractOcrTest {

    private static final byte[] FOTO = {(byte) 0xFF, (byte) 0xD8, 1, 2};

    @TempDir
    Path pasta;

    @Test
    @DisplayName("devolve o que o Tesseract escreveu e apaga a foto do disco")
    void leOTexto() throws IOException {
        // O script responde com o caminho da foto e as permissoes dela.
        TesseractOcr ocr = new TesseractOcr(script("echo \"$1\"; ls -l \"$1\" | cut -c1-10"), Duration.ofSeconds(10));

        String[] saida = ocr.ler(FOTO).orElseThrow().split("\n");

        assertThat(saida[1]).isEqualTo("-rw-------");
        assertThat(Path.of(saida[0])).doesNotExist();
    }

    @Test
    @DisplayName("Tesseract com erro, travado ou ausente: vazio, e a leitura segue para o Gemini")
    void semOcr() throws IOException {
        assertThat(new TesseractOcr(script("exit 3"), Duration.ofSeconds(10)).ler(FOTO)).isEmpty();
        assertThat(new TesseractOcr(script("sleep 5"), Duration.ofMillis(200)).ler(FOTO)).isEmpty();
        assertThat(new TesseractOcr(pasta.resolve("nao-existe").toString(), Duration.ofSeconds(1)).ler(FOTO)).isEmpty();
        assertThat(new TesseractOcr(" ", Duration.ofSeconds(1)).ler(FOTO)).isEmpty();
        assertThat(new TesseractOcr(null, Duration.ofSeconds(1)).ler(FOTO)).isEmpty();
    }

    @Test
    @DisplayName("interrompido no meio, devolve vazio e mantem o aviso de interrupcao")
    void interrompido() throws IOException {
        TesseractOcr ocr = new TesseractOcr(script("sleep 5"), Duration.ofSeconds(10));

        Thread.currentThread().interrupt();
        try {
            Optional<String> texto = ocr.ler(FOTO);
            assertThat(texto).isEmpty();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private String script(String corpo) throws IOException {
        Path arquivo = Files.createTempFile(pasta, "tesseract-", ".sh");
        Files.writeString(arquivo, "#!/bin/sh\n" + corpo + "\n");
        Files.setPosixFilePermissions(arquivo, PosixFilePermissions.fromString("rwx------"));
        return arquivo.toString();
    }
}
