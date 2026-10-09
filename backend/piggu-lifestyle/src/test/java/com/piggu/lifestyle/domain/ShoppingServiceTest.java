package com.piggu.lifestyle.domain;

import com.piggu.common.error.ForbiddenException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.lifestyle.api.dto.ShoppingItemRequest;
import com.piggu.lifestyle.api.dto.ShoppingItemResponse;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Listas de compras e desejos.
 *
 * <p>A regra que mais importa aqui e de seguranca: a URL de imagem vem de um
 * catalogo externo e termina dentro de uma tag img na pagina. Aceitar qualquer
 * esquema seria deixar terceiro escolher o que a pagina carrega.</p>
 */
class ShoppingServiceTest extends PostgresIntegrationTest {

    private static final CurrentUser TITULAR =
            new CurrentUser(UUID.randomUUID(), "titular@piggu.test", PigguRole.TITULAR);

    @Autowired
    private ShoppingService compras;

    @Autowired
    private ShoppingItemRepository repositorio;

    @BeforeEach
    void limpar() {
        repositorio.deleteAll();
    }

    @ParameterizedTest(name = "recusa imagem em {0}")
    @DisplayName("so aceita imagem do catalogo, por HTTPS")
    @ValueSource(strings = {
            "http://exemplo.test/foto.jpg",
            "javascript:alert(1)",
            "data:image/png;base64,AAAA",
            "//exemplo.test/foto.jpg",
            "ftp://exemplo.test/foto.jpg",
            "https://rastreador.test/pixel.gif",
            "https://images.openfoodfacts.org.rastreador.test/x.jpg",
            "http://images.openfoodfacts.org/x.jpg"
    })
    void recusaImagemInsegura(String url) {
        ShoppingItemResponse item = criar(new ShoppingItemRequest("Ovos", "12", null, null, url, null));

        assertThat(item.imagem()).isEmpty();
    }

    @Test
    @DisplayName("imagem do Open Food Facts e preservada")
    void aceitaImagemDoCatalogo() {
        String url = "https://images.openfoodfacts.org/images/products/789/front_pt.jpg";
        assertThat(criar(new ShoppingItemRequest("Ovos", "12", null, null, url, null)).imagem())
                .isEqualTo(url);
    }

    @Test
    @DisplayName("codigo de barras guarda so digitos")
    void codigoSomenteDigitos() {
        ShoppingItemResponse item =
                criar(new ShoppingItemRequest("Leite", null, null, null, null, "  56-01234 abc 9 "));

        assertThat(item.codigo()).isEqualTo("5601234 9".replace(" ", ""));
    }

    @Test
    @DisplayName("lista desconhecida cai em Compras")
    void listaDesconhecidaCaiEmCompras() {
        assertThat(criar(new ShoppingItemRequest("X", null, "Inventada", null, null, null)).lista())
                .isEqualTo("Compras");
        assertThat(criar(new ShoppingItemRequest("Y", null, null, null, null, null)).lista())
                .isEqualTo("Compras");
    }

    @Test
    @DisplayName("lista de desejos e reconhecida em qualquer caixa")
    void reconheceDesejos() {
        assertThat(criar(new ShoppingItemRequest("Z", null, "desejos", null, null, null)).lista())
                .isEqualTo("Desejos");
    }

    @Test
    @DisplayName("filtro devolve so a lista pedida")
    void filtraPorLista() {
        criar(new ShoppingItemRequest("Ovos", null, "Compras", null, null, null));
        criar(new ShoppingItemRequest("Bicicleta", null, "Desejos", null, null, null));

        assertThat(compras.listar("Compras")).hasSize(1);
        assertThat(compras.listar("Desejos")).hasSize(1);
        assertThat(compras.listar(null)).hasSize(2);
    }

    @Test
    @DisplayName("marcar comprado e reversivel")
    void marcarComprado() {
        ShoppingItemResponse item = criar(new ShoppingItemRequest("Ovos", null, null, null, null, null));

        assertThat(compras.alternarComprado(item.id(), true).comprado()).isTrue();
        assertThat(compras.alternarComprado(item.id(), false).comprado()).isFalse();
    }

    @Test
    @DisplayName("ninguem apaga item de outra pessoa")
    void naoApagaItemAlheio() {
        ShoppingItemResponse item = criar(new ShoppingItemRequest("Ovos", null, null, null, null, null));
        CurrentUser outra = new CurrentUser(UUID.randomUUID(), "membro@piggu.test", PigguRole.MEMBRO);

        assertThatThrownBy(() -> compras.excluir(item.id(), outra))
                .isInstanceOf(ForbiddenException.class);
    }

    private ShoppingItemResponse criar(ShoppingItemRequest pedido) {
        return compras.criar(pedido, TITULAR.email());
    }
}
