package com.piggu.lifestyle.domain;

import com.piggu.common.error.NotFoundException;
import com.piggu.common.web.Meses;
import com.piggu.lifestyle.api.dto.PersonalGoalRequest;
import com.piggu.lifestyle.api.dto.PersonalGoalResponse;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Metas do mes.
 *
 * <p>O que importa aqui: a meta e so de quem criou, nasce no mes corrente e some da
 * lista padrao quando o mes vira, sem sair do banco.</p>
 */
class PersonalGoalServiceTest extends PostgresIntegrationTest {

    private static final UUID ANA = UUID.nameUUIDFromBytes("ana@piggu.test".getBytes());
    private static final UUID BIA = UUID.nameUUIDFromBytes("bia@piggu.test".getBytes());

    @Autowired
    private PersonalGoalService metas;

    @Autowired
    private PersonalGoalRepository repositorio;

    @BeforeEach
    void limpar() {
        repositorio.deleteAll();
    }

    @Test
    @DisplayName("nasce no mes atual, sem progresso vira zero e so fica concluida ao chegar no alvo")
    void criaEConclui() {
        PersonalGoalResponse meta = metas.criar(new PersonalGoalRequest("  Ler livros  ", 3, null), ANA);

        assertThat(meta.titulo()).isEqualTo("Ler livros");
        assertThat(meta.atual()).isZero();
        assertThat(meta.mes()).isEqualTo(YearMonth.now(Meses.BRASILIA).toString());
        assertThat(meta.concluida()).isFalse();

        assertThat(metas.atualizar(meta.id(), new PersonalGoalRequest("Ler livros", 3, 2), ANA).concluida()).isFalse();
        PersonalGoalResponse concluida = metas.atualizar(meta.id(), new PersonalGoalRequest("Ler livros", 3, 3), ANA);
        assertThat(concluida.concluida()).isTrue();
        assertThat(concluida.atual()).isEqualTo(3);
        // Passar do alvo continua concluida.
        assertThat(metas.atualizar(meta.id(), new PersonalGoalRequest("Ler livros", 3, 5), ANA).concluida()).isTrue();
    }

    @Test
    @DisplayName("lista so as do mes pedido, na ordem em que foram criadas; as antigas ficam no banco")
    void filtroPorMes() {
        YearMonth atual = YearMonth.now(Meses.BRASILIA);
        repositorio.save(new PersonalGoal(atual.minusMonths(1), "Do mes passado", 5, 1, ANA));
        metas.criar(new PersonalGoalRequest("Primeira", 2, 0), ANA);
        metas.criar(new PersonalGoalRequest("Segunda", 4, 1), ANA);

        assertThat(metas.listar(atual, ANA)).extracting(PersonalGoalResponse::titulo)
                .containsExactly("Primeira", "Segunda");
        assertThat(metas.listar(atual.minusMonths(1), ANA)).extracting(PersonalGoalResponse::titulo)
                .containsExactly("Do mes passado");
        assertThat(repositorio.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("meta de outra pessoa nao aparece e responde como inexistente")
    void soADona() {
        PersonalGoalResponse meta = metas.criar(new PersonalGoalRequest("Academia", 8, 0), ANA);
        YearMonth atual = YearMonth.now(Meses.BRASILIA);

        assertThat(metas.listar(atual, BIA)).isEmpty();
        assertThatThrownBy(() -> metas.atualizar(meta.id(), new PersonalGoalRequest("Outra", 1, 1), BIA))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> metas.excluir(meta.id(), BIA)).isInstanceOf(NotFoundException.class);
        assertThat(metas.listar(atual, ANA)).extracting(PersonalGoalResponse::titulo).containsExactly("Academia");

        metas.excluir(meta.id(), ANA);
        assertThat(metas.listar(atual, ANA)).isEmpty();
    }
}
