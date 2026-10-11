package com.piggu.finance.domain;

import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.finance.api.dto.ExpenseItemRequest;
import com.piggu.finance.api.dto.ProductResponse;
import com.piggu.finance.api.dto.SaveExpensesRequest;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memoria de precos alimentada pelos gastos.
 *
 * <p>Media movel, minimo, maximo e variacao da ultima compra sao o que o painel de
 * mercado mostra. Um erro aqui nao quebra nada visivelmente: so passa a exibir
 * numeros errados, que e pior.</p>
 */
class ProductMemoryServiceTest extends PostgresIntegrationTest {

    private static final CurrentUser TITULAR =
            new CurrentUser(UUID.randomUUID(), "titular@piggu.test", PigguRole.TITULAR);

    @Autowired
    private ExpenseService gastos;

    @Autowired
    private ProductMemoryService memoria;

    @Autowired
    private ProductMemoryRepository repositorio;

    @Autowired
    private ExpenseRepository repositorioDeGastos;

    @BeforeEach
    void limpar() {
        repositorio.deleteAll();
        repositorioDeGastos.deleteAll();
    }

    @Test
    @DisplayName("primeira compra vira ultimo, minimo, maximo e media, sem variacao")
    void primeiraCompra() {
        comprar("Leite Mimosa 1L", "1.29", LocalDate.of(2026, 9, 10));

        ProductResponse leite = memoria.listar().get(0);
        assertThat(leite.compras()).isEqualTo(1);
        assertThat(leite.ultimo()).isEqualByComparingTo("1.29");
        assertThat(leite.menor()).isEqualByComparingTo("1.29");
        assertThat(leite.maior()).isEqualByComparingTo("1.29");
        assertThat(leite.media()).isEqualByComparingTo("1.29");
        assertThat(leite.variacao()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("grafias diferentes do mesmo produto somam no mesmo historico")
    void grafiasDiferentesSomamJuntas() {
        comprar("Leite Mimosa 1L", "1.29", LocalDate.of(2026, 9, 10));
        comprar("leite mimosa 1 l", "1.59", LocalDate.of(2026, 9, 12));

        List<ProductResponse> produtos = memoria.listar();
        assertThat(produtos).hasSize(1);

        ProductResponse leite = produtos.get(0);
        assertThat(leite.compras()).isEqualTo(2);
        assertThat(leite.ultimo()).isEqualByComparingTo("1.59");
        assertThat(leite.menor()).isEqualByComparingTo("1.29");
        assertThat(leite.maior()).isEqualByComparingTo("1.59");
        assertThat(leite.media()).isEqualByComparingTo("1.44");
        assertThat(leite.variacao()).isEqualByComparingTo("0.30");
        assertThat(leite.data()).isEqualTo(LocalDate.of(2026, 9, 12));
    }

    @Test
    @DisplayName("preco que cai gera variacao negativa e mantem o minimo")
    void precoQueCai() {
        comprar("Cafe Delta", "4.00", LocalDate.of(2026, 9, 1));
        comprar("Cafe Delta", "3.00", LocalDate.of(2026, 9, 8));

        ProductResponse cafe = memoria.listar().get(0);
        assertThat(cafe.variacao()).isEqualByComparingTo("-1.00");
        assertThat(cafe.menor()).isEqualByComparingTo("3.00");
        assertThat(cafe.maior()).isEqualByComparingTo("4.00");
        assertThat(cafe.media()).isEqualByComparingTo("3.50");
    }

    @Test
    @DisplayName("media movel acompanha tres compras")
    void mediaMovelDeTresCompras() {
        comprar("Arroz", "1.00", LocalDate.of(2026, 9, 1));
        comprar("Arroz", "2.00", LocalDate.of(2026, 9, 2));
        comprar("Arroz", "3.00", LocalDate.of(2026, 9, 3));

        ProductResponse arroz = memoria.listar().get(0);
        assertThat(arroz.compras()).isEqualTo(3);
        assertThat(arroz.media()).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("lista vem ordenada pelos produtos mais comprados")
    void ordenadoPorFrequencia() {
        comprar("Arroz", "1.00", LocalDate.now());
        comprar("Feijao", "2.00", LocalDate.now());
        comprar("Feijao", "2.00", LocalDate.now());

        assertThat(memoria.listar()).extracting(ProductResponse::nome).containsExactly("Feijao", "Arroz");
    }

    @Test
    @DisplayName("memoria enviada a IA traz nome e categoria")
    void memoriaParaIa() {
        comprar("Leite Mimosa 1L", "1.29", LocalDate.now());

        assertThat(memoria.memoriaParaIa(10)).containsExactly("Leite Mimosa 1L = Alimentação");
    }

    private void comprar(String item, String valor, LocalDate data) {
        gastos.salvar(new SaveExpensesRequest(
                data, "Mercado", null, "Manual",
                List.of(new ExpenseItemRequest(item, "Alimentação", new BigDecimal(valor), "Variavel"))
        ), TITULAR.id());
    }
}
