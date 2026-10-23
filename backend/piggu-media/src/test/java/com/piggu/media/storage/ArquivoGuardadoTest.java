package com.piggu.media.storage;

import com.piggu.media.storage.StoragePort.ArquivoGuardado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** O arquivo lido compara pelo conteudo dos bytes, e o toString nao despeja os bytes no log. */
class ArquivoGuardadoTest {

    @Test
    @DisplayName("dois arquivos com os mesmos bytes sao iguais")
    void comparaPeloConteudo() {
        ArquivoGuardado foto = new ArquivoGuardado("image/png", new byte[] {1, 2, 3});

        assertThat(foto)
                .isEqualTo(new ArquivoGuardado("image/png", new byte[] {1, 2, 3}))
                .hasSameHashCodeAs(new ArquivoGuardado("image/png", new byte[] {1, 2, 3}))
                .isNotEqualTo(new ArquivoGuardado("image/png", new byte[] {1, 2, 4}))
                .isNotEqualTo(new ArquivoGuardado("image/jpeg", new byte[] {1, 2, 3}))
                .isNotEqualTo("image/png");
        assertThat(new ArquivoGuardado(null, null)).isEqualTo(new ArquivoGuardado(null, null));
    }

    @Test
    @DisplayName("o toString mostra o tipo e o tamanho, nunca os bytes")
    void toStringSemOsBytes() {
        assertThat(new ArquivoGuardado("image/png", new byte[] {1, 2, 3}))
                .hasToString("ArquivoGuardado[contentType=image/png, bytes=3]");
        assertThat(new ArquivoGuardado("image/png", null))
                .hasToString("ArquivoGuardado[contentType=image/png, bytes=0]");
    }
}
