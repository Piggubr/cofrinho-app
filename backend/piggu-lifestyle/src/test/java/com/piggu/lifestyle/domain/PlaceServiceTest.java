package com.piggu.lifestyle.domain;

import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.lifestyle.api.dto.PlaceRequest;
import com.piggu.lifestyle.api.dto.PlaceResponse;
import com.piggu.lifestyle.integration.MediaClient;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lugares visitados.
 *
 * <p>A foto mora no servico de media; o que importa aqui e a ordem: a nova entra antes de
 * a antiga sair, e apagar o lugar leva a foto junto.</p>
 */
class PlaceServiceTest extends PostgresIntegrationTest {

    private static final UUID TITULAR = UUID.nameUUIDFromBytes("titular@piggu.test".getBytes());
    private static final UUID MEMBRO = UUID.nameUUIDFromBytes("membro@piggu.test".getBytes());

    @Autowired
    private PlaceService lugares;

    @Autowired
    private PlaceRepository repositorio;

    @MockitoBean
    private MediaClient media;

    @BeforeEach
    void limpar() {
        repositorio.deleteAll();
        reset(media);
    }

    @Test
    @DisplayName("sem categoria vira Outros, sem valor vira zero e marcador desconhecido sai")
    void padroes() {
        PlaceResponse lugar = lugares.criar(pedido("Padaria", null, null, List.of("Favorito", "Nada"), null),
                TITULAR);

        assertThat(lugar.categoria()).isEqualTo("Outros");
        assertThat(lugar.valor()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(lugar.marcacoes()).containsExactly("Favorito");
        assertThat(lugar.temFoto()).isFalse();
        assertThat(lugar.usuario()).isEqualTo(TITULAR);
        verify(media, never()).enviar(any(), any(), any());
    }

    @Test
    @DisplayName("lista do mais recente para o mais antigo")
    void ordemDaLista() {
        lugares.criar(pedido("Antigo", LocalDate.of(2026, 1, 5)), TITULAR);
        lugares.criar(pedido("Novo", LocalDate.of(2026, 9, 1)), TITULAR);

        assertThat(lugares.listar()).extracting(PlaceResponse::nome).containsExactly("Novo", "Antigo");
    }

    @Test
    @DisplayName("com foto, envia ao media e guarda so o identificador")
    void criaComFoto() {
        UUID foto = UUID.randomUUID();
        when(media.enviar("aGVsbG8=", "image/png", "LUGAR")).thenReturn(foto);

        PlaceResponse lugar = lugares.criar(pedido("Praia", null, new BigDecimal("80"), List.of(), "aGVsbG8="),
                TITULAR);

        assertThat(lugar.fotoAssetId()).isEqualTo(foto);
        assertThat(lugar.temFoto()).isTrue();
    }

    @Test
    @DisplayName("trocar a foto envia a nova antes de apagar a antiga")
    void trocaDeFoto() {
        UUID antiga = UUID.randomUUID();
        UUID nova = UUID.randomUUID();
        when(media.enviar(any(), any(), eq("LUGAR"))).thenReturn(antiga, nova);
        PlaceResponse lugar = lugares.criar(pedido("Praia", null, null, List.of(), "YQ=="), TITULAR);

        PlaceResponse editado = lugares.atualizar(lugar.id(),
                pedido("Praia do Rosa", null, null, List.of(), "Yg=="), dono(TITULAR));

        assertThat(editado.nome()).isEqualTo("Praia do Rosa");
        assertThat(editado.fotoAssetId()).isEqualTo(nova);
        InOrder ordem = inOrder(media);
        ordem.verify(media).enviar("Yg==", "image/png", "LUGAR");
        ordem.verify(media).apagar(antiga);
    }

    @Test
    @DisplayName("editar sem foto nova mantem a foto")
    void edicaoSemFoto() {
        UUID foto = UUID.randomUUID();
        when(media.enviar(any(), any(), any())).thenReturn(foto);
        PlaceResponse lugar = lugares.criar(pedido("Praia", null, null, List.of(), "YQ=="), TITULAR);

        PlaceResponse editado = lugares.atualizar(lugar.id(), pedido("Praia", null, null, List.of(), " "),
                dono(TITULAR));

        assertThat(editado.fotoAssetId()).isEqualTo(foto);
        verify(media, never()).apagar(any());
    }

    @Test
    @DisplayName("membro nao edita nem apaga lugar de outra pessoa")
    void soODonoMexe() {
        PlaceResponse lugar = lugares.criar(pedido("Padaria", LocalDate.of(2026, 9, 1)), TITULAR);
        CurrentUser membro = new CurrentUser(MEMBRO, "membro@piggu.test", PigguRole.MEMBRO);

        assertThatThrownBy(() -> lugares.atualizar(lugar.id(), pedido("Outro", LocalDate.of(2026, 9, 1)), membro))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> lugares.excluir(lugar.id(), membro)).isInstanceOf(ForbiddenException.class);
        assertThat(lugares.listar()).extracting(PlaceResponse::nome).containsExactly("Padaria");
    }

    @Test
    @DisplayName("apagar o lugar apaga a foto junto")
    void exclusaoLevaAFoto() {
        UUID foto = UUID.randomUUID();
        when(media.enviar(any(), any(), any())).thenReturn(foto);
        PlaceResponse lugar = lugares.criar(pedido("Praia", null, null, List.of(), "YQ=="), TITULAR);

        lugares.excluir(lugar.id(), dono(TITULAR));

        verify(media).apagar(foto);
        assertThat(lugares.listar()).isEmpty();
    }

    @Test
    @DisplayName("lugar que nao existe da 404")
    void naoEncontrado() {
        assertThatThrownBy(() -> lugares.excluir(UUID.randomUUID(), dono(TITULAR)))
                .isInstanceOf(NotFoundException.class);
    }

    private static CurrentUser dono(UUID id) {
        return new CurrentUser(id, "titular@piggu.test", PigguRole.TITULAR);
    }

    private static PlaceRequest pedido(String nome, LocalDate data) {
        return new PlaceRequest(nome, "Restaurante", "Centro", 4, "", data, List.of(), null, null, null);
    }

    private static PlaceRequest pedido(String nome, String categoria, BigDecimal valor, List<String> marcacoes,
                                       String foto) {
        return new PlaceRequest(nome, categoria, "Centro", 5, "Bom", LocalDate.of(2026, 9, 1), marcacoes, valor,
                foto, foto == null ? null : "image/png");
    }
}
