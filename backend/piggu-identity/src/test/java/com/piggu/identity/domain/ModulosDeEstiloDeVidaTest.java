package com.piggu.identity.domain;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.BusinessException;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Compras, lugares, filmes, fotos e premios: cada pessoa liga no perfil o que quer no menu. */
class ModulosDeEstiloDeVidaTest extends PostgresIntegrationTest {

    @Autowired
    private AuthService auth;

    @Autowired
    private FamiliaService familias;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("conta nova nasce sem modulos; ligar devolve na ordem do menu, sem repetir")
    void contaNovaEEscolha() {
        UserAccount conta = familias.criarConta("modulos-" + System.nanoTime() + "@piggu.test", "Ana",
                Consentimentos.VERSAO_DO_AVISO);
        assertThat(auth.perfil(conta.getId()).preferencias().modulos()).isEmpty();

        assertThat(auth.escolherModulos(conta.getId(), List.of("Filmes", "compras", "filmes")).preferencias().modulos())
                .containsExactly("compras", "filmes");
        assertThat(auth.escolherModulos(conta.getId(), List.of()).preferencias().modulos()).isEmpty();
    }

    @Test
    @DisplayName("conta de antes da escolha (coluna vazia no banco) continua com todos; modulo inventado e recusado")
    void contaAntigaEModuloDesconhecido() {
        UserAccount conta = familias.criarConta("antiga-" + System.nanoTime() + "@piggu.test", "Bia",
                Consentimentos.VERSAO_DO_AVISO);
        jdbc.update("UPDATE users SET lifestyle_modules = NULL WHERE id = ?", conta.getId());

        assertThat(auth.perfil(conta.getId()).preferencias().modulos()).isEqualTo(ModulosDeEstiloDeVida.TODOS);
        assertThatThrownBy(() -> auth.escolherModulos(conta.getId(), List.of("cassino")))
                .isInstanceOf(BusinessException.class);
    }
}
