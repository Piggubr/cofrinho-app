package com.piggu.media.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.media.storage.StoragePort;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guarda de imagens.
 *
 * <p>Os limites vem do Apps Script: cinco megabytes e apenas jpeg, png e webp.
 * O que o front manda nem sempre e o que o contrato diz, entao o servico precisa
 * aguentar a data URL inteira e tipos fora da lista sem quebrar.</p>
 */
class AssetServiceTest extends PostgresIntegrationTest {

    private static final CurrentUser TITULAR =
            new CurrentUser(UUID.randomUUID(), "titular@piggu.test", PigguRole.TITULAR);

    @Autowired
    private AssetService assets;

    @Autowired
    private AssetRepository repositorio;

    @Autowired
    private StoragePort armazenamento;

    @BeforeEach
    void limpar() {
        repositorio.deleteAll();
    }

    @Test
    @DisplayName("imagem guardada volta com o mesmo conteudo")
    void guardaEDevolve() {
        byte[] original = png(1, 2, 3, 4, 5);

        Asset asset = guardar(original, "image/png");
        StoragePort.ArquivoGuardado lido = assets.baixar(asset.getId(), TITULAR.id());

        assertThat(asset.getContentType()).isEqualTo("image/png");
        assertThat(asset.getSizeBytes()).isEqualTo(original.length);
        assertThat(lido.conteudo()).isEqualTo(original);
    }

    @Test
    @DisplayName("o tipo sai dos bytes, nao do que o cliente diz")
    void tipoPeloConteudo() {
        assertThat(guardar(jpeg(), "image/png").getContentType()).isEqualTo("image/jpeg");
        assertThat(guardar(png(), null).getContentType()).isEqualTo("image/png");
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 1};
        assertThat(guardar(webp, "texto/estranho").getContentType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("o que nao e JPEG, PNG nem WebP e recusado, mesmo dizendo que e imagem")
    void recusaQuemNaoEImagem() {
        byte[] html = "<script>alert(1)</script>".getBytes();
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a'};

        assertThatThrownBy(() -> guardar(html, "image/jpeg")).isInstanceOf(BusinessException.class)
                .hasMessageContaining("JPEG, PNG ou WebP");
        assertThatThrownBy(() -> guardar(gif, "image/gif")).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("data URL completa e aceita, nao so o base64 puro")
    void aceitaDataUrlCompleta() {
        String base64 = Base64.getEncoder().encodeToString(png(9, 8, 7));

        Asset asset = assets.guardar(
                "data:image/png;base64," + base64, "image/png", "FEED", null, TITULAR.id());

        assertThat(assets.baixar(asset.getId(), TITULAR.id()).conteudo()).isEqualTo(png(9, 8, 7));
    }

    @Test
    @DisplayName("quebras de linha no base64 nao derrubam o envio")
    void aceitaBase64ComQuebras() {
        String base64 = Base64.getEncoder().encodeToString(png(4, 5, 6));
        String comQuebras = base64.substring(0, 2) + "\n" + base64.substring(2);

        Asset asset = assets.guardar(comQuebras, "image/png", "FEED", null, TITULAR.id());

        assertThat(assets.baixar(asset.getId(), TITULAR.id()).conteudo()).isEqualTo(png(4, 5, 6));
    }

    @Test
    @DisplayName("base64 invalido vira erro de negocio, nao falha interna")
    void base64InvalidoViraErroDeNegocio() {
        assertThatThrownBy(() -> assets.guardar("nao@@e@@base64", "image/png", "FEED", null, TITULAR.id()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("formato que nao consegui ler");
    }

    @Test
    @DisplayName("foto acima de cinco megabytes e recusada")
    void recusaFotoGrande() {
        byte[] grande = new byte[AssetService.TAMANHO_MAXIMO + 1];
        System.arraycopy(jpeg(), 0, grande, 0, 3);

        assertThatThrownBy(() -> guardar(grande, "image/jpeg"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    @DisplayName("contexto ausente cai no padrao")
    void contextoPadrao() {
        Asset asset = assets.guardar(
                Base64.getEncoder().encodeToString(png()), "image/png", null, null, TITULAR.id());

        assertThat(asset.getContext()).isEqualTo(Asset.CONTEXTO_PADRAO);
    }

    @Test
    @DisplayName("foto inexistente da erro claro")
    void fotoInexistente() {
        assertThatThrownBy(() -> assets.baixar(UUID.randomUUID(), TITULAR.id()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Foto nao encontrada.");
    }

    @Test
    @DisplayName("so quem enviou apaga a foto; nem o admin apaga a de outro adulto")
    void exclusaoRespeitaDono() {
        Asset asset = guardar(png(), "image/png");
        CurrentUser outra = new CurrentUser(UUID.randomUUID(), "membro@piggu.test", PigguRole.MEMBRO);
        CurrentUser admin = new CurrentUser(UUID.randomUUID(), "admin@piggu.test", PigguRole.ADMIN);

        assertThatThrownBy(() -> assets.apagar(asset.getId(), outra)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> assets.apagar(asset.getId(), admin)).isInstanceOf(ForbiddenException.class);

        assets.apagar(asset.getId(), TITULAR);
        assertThat(repositorio.count()).isZero();

    }

    @Test
    @DisplayName("foto do Girls Math so a dona ve e apaga, nem o titular nem o admin")
    void fotoPrivada() {
        CurrentUser dona = new CurrentUser(UUID.randomUUID(), "dona@piggu.test", PigguRole.PARCEIRO);
        CurrentUser admin = new CurrentUser(UUID.randomUUID(), "admin@piggu.test", PigguRole.ADMIN);
        Asset asset = assets.guardar(Base64.getEncoder().encodeToString(png(1)), "image/png", "GIRLS_MATH", null,
                dona.id());

        assertThat(assets.baixar(asset.getId(), dona.id()).conteudo()).isEqualTo(png(1));
        assertThatThrownBy(() -> assets.baixar(asset.getId(), TITULAR.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> assets.apagar(asset.getId(), TITULAR)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> assets.apagar(asset.getId(), admin)).isInstanceOf(NotFoundException.class);

        assets.apagar(asset.getId(), dona);
        assertThat(repositorio.count()).isZero();
    }

    @Test
    @DisplayName("a familia que chegou ao teto nao guarda mais fotos, nem as privadas contam de fora")
    void tetoDaFamilia() {
        AssetService comTetoDeUmMega = new AssetService(repositorio, armazenamento, 1);
        String seiscentosKb = Base64.getEncoder().encodeToString(png(new int[600 * 1024]));
        comTetoDeUmMega.guardar(seiscentosKb, "image/png", "GIRLS_MATH", null, TITULAR.id());

        assertThatThrownBy(() -> comTetoDeUmMega.guardar(seiscentosKb, "image/png", "FEED", null, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("limite de 1 MB");
        assertThat(repositorio.count()).isEqualTo(1);
    }

    private Asset guardar(byte[] conteudo, String tipo) {
        return assets.guardar(
                Base64.getEncoder().encodeToString(conteudo), tipo, "FEED", null, TITULAR.id());
    }

    private static byte[] png(int... resto) {
        return comCabecalho(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, resto);
    }

    private static byte[] jpeg(int... resto) {
        return comCabecalho(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, resto);
    }

    private static byte[] comCabecalho(byte[] cabecalho, int... resto) {
        byte[] tudo = Arrays.copyOf(cabecalho, cabecalho.length + resto.length);
        for (int i = 0; i < resto.length; i++) {
            tudo[cabecalho.length + i] = (byte) resto[i];
        }
        return tudo;
    }
}
