package com.piggu.finance.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Categorias de gasto.
 *
 * <p>A decisao herdada que mais importa: categoria desconhecida nao derruba o
 * lancamento, cai em Outros. Perder o gasto seria pior do que classifica-lo mal.</p>
 */
class CategoryServiceTest extends PostgresIntegrationTest {

    @Autowired
    private CategoryService categorias;

    @Autowired
    private CustomCategoryRepository repositorio;

    @BeforeEach
    void limpar() {
        repositorio.deleteAll();
    }

    @Test
    @DisplayName("as oito categorias de fabrica estao sempre presentes")
    void categoriasDeFabrica() {
        assertThat(categorias.listar()).containsExactlyElementsOf(Categorias.BASE);
    }

    @Test
    @DisplayName("categoria criada entra na lista depois das de fabrica")
    void categoriaCriadaEntraNaLista() {
        categorias.criar("Pets", "titular@piggu.test");

        assertThat(categorias.listar()).hasSize(Categorias.BASE.size() + 1).endsWith("Pets");
    }

    @Test
    @DisplayName("categoria desconhecida vira Outros em vez de recusar o gasto")
    void desconhecidaViraOutros() {
        assertThat(categorias.normalizar("NaoExiste")).isEqualTo(Categorias.PADRAO);
        assertThat(categorias.normalizar(null)).isEqualTo(Categorias.PADRAO);
        assertThat(categorias.normalizar("  ")).isEqualTo(Categorias.PADRAO);
    }

    @Test
    @DisplayName("categoria valida e devolvida na grafia oficial")
    void normalizaGrafia() {
        assertThat(categorias.normalizar("alimentação")).isEqualTo("Alimentação");
        assertThat(categorias.normalizar("  LAZER ")).isEqualTo("Lazer");
    }

    @Test
    @DisplayName("duplicata e recusada mesmo com caixa diferente")
    void duplicataRecusada() {
        categorias.criar("Pets", "titular@piggu.test");

        assertThatThrownBy(() -> categorias.criar("pets", "titular@piggu.test"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Essa opcao ja existe.");

        assertThatThrownBy(() -> categorias.criar("Lazer", "titular@piggu.test"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("nome curto demais e recusado")
    void nomeCurtoRecusado() {
        assertThatThrownBy(() -> categorias.criar("a", "titular@piggu.test"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Digite um nome valido.");
    }

    @Test
    @DisplayName("espacos repetidos no nome sao colapsados antes de gravar")
    void colapsaEspacos() {
        assertThat(categorias.criar("Casa   e   Jardim", "titular@piggu.test"))
                .isEqualTo("Casa e Jardim");
    }
}
