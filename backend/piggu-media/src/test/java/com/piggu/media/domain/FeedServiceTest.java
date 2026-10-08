package com.piggu.media.domain;

import com.piggu.common.error.ForbiddenException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.media.api.dto.FeedPhotoRequest;
import com.piggu.media.api.dto.FeedPhotoResponse;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feed de fotos por mes.
 *
 * <p>A foto e o registro do feed sao coisas separadas: apagar o registro precisa
 * levar o arquivo junto, senao a imagem fica orfa no armazenamento para sempre.</p>
 */
class FeedServiceTest extends PostgresIntegrationTest {

    private static final CurrentUser TITULAR =
            new CurrentUser(UUID.randomUUID(), "titular@piggu.test", PigguRole.TITULAR);

    @Autowired
    private FeedService feed;

    @Autowired
    private FeedPhotoRepository repositorio;

    @Autowired
    private AssetRepository assets;

    @BeforeEach
    void limpar() {
        repositorio.deleteAll();
        assets.deleteAll();
    }

    @Test
    @DisplayName("publicar cria a foto e o registro do mes")
    void publicar() {
        FeedPhotoResponse foto = publicar("2026-09");

        assertThat(foto.mesKey()).isEqualTo("2026-09");
        assertThat(foto.assetId()).isNotNull();
        assertThat(foto.legenda()).isEmpty();
        assertThat(foto.usuario()).isEqualTo(TITULAR.email());
        assertThat(assets.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("legenda pode ser adicionada depois")
    void legendar() {
        FeedPhotoResponse foto = publicar("2026-09");

        assertThat(feed.legendar(foto.id(), "Nosso setembro", TITULAR).legenda())
                .isEqualTo("Nosso setembro");
    }

    @Test
    @DisplayName("legenda longa demais e cortada no limite da coluna")
    void legendaCortada() {
        FeedPhotoResponse foto = publicar("2026-09");

        assertThat(feed.legendar(foto.id(), "x".repeat(400), TITULAR).legenda()).hasSize(300);
    }

    @Test
    @DisplayName("apagar a foto do feed leva o arquivo junto")
    void apagarLevaOArquivo() {
        FeedPhotoResponse foto = publicar("2026-09");
        assertThat(assets.count()).isEqualTo(1);

        feed.excluir(foto.id(), TITULAR);

        assertThat(feed.listar(null)).isEmpty();
        assertThat(assets.count()).as("arquivo nao pode ficar orfao").isZero();
    }

    @Test
    @DisplayName("filtro por mes separa os meses")
    void filtraPorMes() {
        publicar("2026-08");
        publicar("2026-09");
        publicar("2026-09");

        assertThat(feed.listar("2026-09")).hasSize(2);
        assertThat(feed.listar("2026-08")).hasSize(1);
        assertThat(feed.listar(null)).hasSize(3);
    }

    @Test
    @DisplayName("meses de um digito e de dois digitos funcionam na pasta")
    void mesesDeQualquerDigito() {
        assertThat(publicar("2026-01").mesKey()).isEqualTo("2026-01");
        assertThat(publicar("2026-12").mesKey()).isEqualTo("2026-12");
    }

    @Test
    @DisplayName("ninguem apaga foto de outra pessoa")
    void naoApagaFotoAlheia() {
        FeedPhotoResponse foto = publicar("2026-09");
        CurrentUser outra = new CurrentUser(UUID.randomUUID(), "membro@piggu.test", PigguRole.MEMBRO);

        assertThatThrownBy(() -> feed.excluir(foto.id(), outra)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> feed.legendar(foto.id(), "x", outra)).isInstanceOf(ForbiddenException.class);
    }

    private FeedPhotoResponse publicar(String mesKey) {
        return feed.publicar(new FeedPhotoRequest(
                mesKey, Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}), "image/png"), TITULAR.email());
    }
}
