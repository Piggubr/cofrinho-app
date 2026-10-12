package com.piggu.lifestyle.domain;

import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.common.web.Meses;
import com.piggu.lifestyle.api.dto.GirlsMathRequest;
import com.piggu.lifestyle.api.dto.GirlsMathResponse;
import com.piggu.lifestyle.integration.MediaClient;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Girls Math.
 *
 * <p>Privado de cada pessoa; a foto mora no servico de media e sai junto com o item.</p>
 */
class GirlsMathServiceTest extends PostgresIntegrationTest {

    private static final CurrentUser ANA =
            new CurrentUser(UUID.nameUUIDFromBytes("ana@piggu.test".getBytes()), "ana@piggu.test", PigguRole.TITULAR);
    private static final CurrentUser BIA =
            new CurrentUser(UUID.nameUUIDFromBytes("bia@piggu.test".getBytes()), "bia@piggu.test", PigguRole.MEMBRO);

    @Autowired
    private GirlsMathService girlsMath;

    @Autowired
    private GirlsMathItemRepository repositorio;

    @MockitoBean
    private MediaClient media;

    @BeforeEach
    void limpar() {
        repositorio.deleteAll();
        reset(media);
    }

    @Test
    @DisplayName("sem data vira hoje (Brasilia), sem loja vira vazio e sem foto nao chama o media")
    void padroes() {
        GirlsMathResponse item = girlsMath.criar(pedido("Bolsa", null, null, null), ANA);

        assertThat(item.data()).isEqualTo(LocalDate.now(Meses.BRASILIA));
        assertThat(item.loja()).isEmpty();
        assertThat(item.valor()).isEqualByComparingTo("349.90");
        assertThat(item.temFoto()).isFalse();
        verify(media, never()).enviar(any(), any(), any());
    }

    @Test
    @DisplayName("lista da data mais recente para a mais antiga")
    void ordem() {
        girlsMath.criar(pedido("Antigo", LocalDate.of(2026, 1, 5), "Loja A", null), ANA);
        girlsMath.criar(pedido("Novo", LocalDate.of(2026, 9, 1), "Loja B", null), ANA);
        girlsMath.criar(pedido("Do meio", LocalDate.of(2026, 5, 1), "Loja C", null), ANA);

        assertThat(girlsMath.listar(ANA.id())).extracting(GirlsMathResponse::produto)
                .containsExactly("Novo", "Do meio", "Antigo");
    }

    @Test
    @DisplayName("com foto, envia ao media com o contexto GIRLS_MATH; apagar o item apaga a foto")
    void fotoVaiEVolta() {
        UUID foto = UUID.randomUUID();
        when(media.enviar("aGVsbG8=", "image/png", "GIRLS_MATH")).thenReturn(foto);

        GirlsMathResponse item = girlsMath.criar(pedido("Tenis", null, "Shopping", "aGVsbG8="), ANA);

        assertThat(item.fotoAssetId()).isEqualTo(foto);
        assertThat(item.temFoto()).isTrue();

        girlsMath.excluir(item.id(), ANA.id());

        verify(media).apagar(foto);
        assertThat(girlsMath.listar(ANA.id())).isEmpty();
    }

    @Test
    @DisplayName("membro registra sem foto; com foto e barrado antes de chegar ao media")
    void membroSemFoto() {
        assertThat(girlsMath.criar(pedido("Batom", null, null, null), BIA).produto()).isEqualTo("Batom");

        assertThatThrownBy(() -> girlsMath.criar(pedido("Batom", null, null, "YQ=="), BIA))
                .isInstanceOf(ForbiddenException.class);
        verify(media, never()).enviar(any(), any(), any());
    }

    @Test
    @DisplayName("item de outra pessoa nao aparece e responde como inexistente")
    void soADona() {
        GirlsMathResponse item = girlsMath.criar(pedido("Vestido", null, null, null), ANA);

        assertThat(girlsMath.listar(BIA.id())).isEmpty();
        assertThatThrownBy(() -> girlsMath.excluir(item.id(), BIA.id())).isInstanceOf(NotFoundException.class);
        assertThat(girlsMath.listar(ANA.id())).hasSize(1);
        verify(media, never()).apagar(any());
    }

    private static GirlsMathRequest pedido(String produto, LocalDate data, String loja, String foto) {
        return new GirlsMathRequest(produto, loja, new BigDecimal("349.90"), data, foto,
                foto == null ? null : "image/png");
    }
}
