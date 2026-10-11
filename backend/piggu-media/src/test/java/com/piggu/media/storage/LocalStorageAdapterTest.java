package com.piggu.media.storage;

import com.piggu.common.error.UpstreamException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pasta local, usada quando o Drive nao esta configurado (desenvolvimento). */
class LocalStorageAdapterTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    @TempDir
    private Path raiz;

    @Test
    @DisplayName("guarda, le e apaga um arquivo na pasta do mes")
    void idaEVolta() {
        LocalStorageAdapter local = new LocalStorageAdapter(raiz.resolve("fotos"));

        String id = local.guardar("x.png", "image/png", PNG, "2026-09 - Setembro");

        assertThat(id).startsWith("2026-09 - Setembro/").endsWith(".png");
        assertThat(Files.exists(raiz.resolve("fotos").resolve(id))).isTrue();
        assertThat(local.ler(id).conteudo()).isEqualTo(PNG);

        local.apagar(id);
        assertThat(Files.exists(raiz.resolve("fotos").resolve(id))).isFalse();
    }

    @Test
    @DisplayName("sem pasta fica na raiz; tipo desconhecido vira .jpg")
    void semPasta() {
        LocalStorageAdapter local = new LocalStorageAdapter(raiz);

        assertThat(local.guardar("x", "image/webp", PNG, null)).doesNotContain("/").endsWith(".webp");
        assertThat(local.guardar("x", null, PNG, " ")).doesNotContain("/").endsWith(".jpg");
    }

    @Test
    @DisplayName("identificador com .. nao sai da pasta de arquivos")
    void naoEscapaDaPasta() throws Exception {
        Path segredo = Files.writeString(raiz.resolve("segredo.txt"), "nao");
        LocalStorageAdapter local = new LocalStorageAdapter(raiz.resolve("fotos"));

        assertThatThrownBy(() -> local.ler("../segredo.txt")).isInstanceOf(UpstreamException.class);
        local.apagar("../segredo.txt");
        assertThat(Files.exists(segredo)).isTrue();
    }

    @Test
    @DisplayName("apagar o que ja sumiu nao e erro; ler o que nao existe e")
    void arquivoAusente() {
        LocalStorageAdapter local = new LocalStorageAdapter(raiz);

        assertThatCode(() -> local.apagar("nao-existe.jpg")).doesNotThrowAnyException();
        assertThatThrownBy(() -> local.ler("nao-existe.jpg")).isInstanceOf(UpstreamException.class);
    }
}
