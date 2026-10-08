package com.piggu.lifestyle.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Marcadores de lugar.
 *
 * <p>Marcador desconhecido e descartado em silencio, em vez de recusar o lugar
 * inteiro: mesma escolha que o Apps Script fazia ao filtrar o array recebido.</p>
 */
class PlaceTagServiceTest extends PostgresIntegrationTest {

    @Autowired
    private PlaceTagService marcadores;

    @Autowired
    private CustomPlaceTagRepository repositorio;

    @BeforeEach
    void limpar() {
        repositorio.deleteAll();
    }

    @Test
    @DisplayName("os quatro marcadores de fabrica estao sempre presentes")
    void marcadoresDeFabrica() {
        assertThat(marcadores.listar()).containsExactlyElementsOf(MarcadoresLugar.BASE);
    }

    @Test
    @DisplayName("marcador desconhecido e descartado, os validos ficam")
    void descartaDesconhecido() {
        List<String> filtrados = marcadores.filtrarValidos(
                Arrays.asList("Favorito", "NaoExiste", "Voltaria"));

        assertThat(filtrados).containsExactly("Favorito", "Voltaria");
    }

    @Test
    @DisplayName("lista nula ou vazia devolve lista vazia, nao nulo")
    void listaVazia() {
        assertThat(marcadores.filtrarValidos(null)).isEmpty();
        assertThat(marcadores.filtrarValidos(List.of())).isEmpty();
    }

    @Test
    @DisplayName("repetidos sao removidos")
    void removeRepetidos() {
        assertThat(marcadores.filtrarValidos(List.of("Favorito", "Favorito"))).hasSize(1);
    }

    @Test
    @DisplayName("marcador criado passa a ser aceito no filtro")
    void marcadorCriadoPassaASerValido() {
        assertThat(marcadores.filtrarValidos(List.of("Pet friendly"))).isEmpty();

        marcadores.criar("Pet friendly", "titular@piggu.test");

        assertThat(marcadores.filtrarValidos(List.of("Pet friendly"))).containsExactly("Pet friendly");
    }

    @Test
    @DisplayName("duplicata e recusada, inclusive contra os de fabrica")
    void duplicataRecusada() {
        assertThatThrownBy(() -> marcadores.criar("favorito", "titular@piggu.test"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Essa opcao ja existe.");
    }

    @Test
    @DisplayName("nome curto demais e recusado")
    void nomeCurto() {
        assertThatThrownBy(() -> marcadores.criar("a", "titular@piggu.test"))
                .isInstanceOf(BusinessException.class);
    }
}
