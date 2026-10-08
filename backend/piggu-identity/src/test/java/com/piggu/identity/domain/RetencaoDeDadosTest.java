package com.piggu.identity.domain;

import com.piggu.common.dados.Consentimentos;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Prazos de retencao do aviso de privacidade, aplicados pelo job diario. */
class RetencaoDeDadosTest extends PostgresIntegrationTest {

    @MockitoBean
    private CascataDeDados cascata;

    @Autowired
    private RetencaoDeDados retencao;

    @Autowired
    private FamiliaService familias;

    @Autowired
    private UserAccountRepository usuarios;

    @Autowired
    private RefreshSessionRepository sessoes;

    @Autowired
    private HouseholdInviteRepository convites;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpar() {
        sessoes.deleteAll();
        convites.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    @DisplayName("conta sem acesso ha 24 meses e conta desativada ha 12 saem; as demais ficam")
    void contasVencidas() {
        UserAccount esquecida = criar("esquecida@x.test");
        UserAccount desativada = criar("desativada@x.test");
        UserAccount ativa = criar("ativa@x.test");
        UserAccount desativadaOntem = criar("ontem@x.test");
        jdbc.update("UPDATE users SET last_login_at = now() - interval '25 months' WHERE id = ?", esquecida.getId());
        jdbc.update("UPDATE users SET active = false, deactivated_at = now() - interval '13 months' WHERE id = ?",
                desativada.getId());
        jdbc.update("UPDATE users SET active = false, deactivated_at = now() - interval '1 day' WHERE id = ?",
                desativadaOntem.getId());

        retencao.aplicar();

        assertThat(usuarios.findById(esquecida.getId())).isEmpty();
        assertThat(usuarios.findById(desativada.getId())).isEmpty();
        assertThat(usuarios.findById(ativa.getId())).isPresent();
        assertThat(usuarios.findById(desativadaOntem.getId())).isPresent();
    }

    @Test
    @DisplayName("sessao e convite vencidos saem")
    void sessoesEConvites() {
        UserAccount conta = criar("sessao@x.test");
        sessoes.save(new RefreshSession(conta.getId(), "vencida", null, Instant.now().minusSeconds(60)));
        sessoes.save(new RefreshSession(conta.getId(), "valida", null, Instant.now().plusSeconds(3600)));
        convites.save(new HouseholdInvite(conta.getHouseholdId(), "velho@x.test", conta.getId(),
                Instant.now().minusSeconds(60)));

        retencao.aplicar();

        assertThat(sessoes.findByTokenHash("vencida")).isEmpty();
        assertThat(sessoes.findByTokenHash("valida")).isPresent();
        assertThat(convites.count()).isZero();
    }

    @Test
    @DisplayName("desativar marca a data")
    void dataDaDesativacao() {
        UserAccount conta = criar("marca@x.test");
        conta.setActive(false);
        usuarios.save(conta);

        assertThat(jdbc.queryForObject("SELECT deactivated_at IS NOT NULL FROM users WHERE id = ?",
                Boolean.class, conta.getId())).isTrue();
    }

    private UserAccount criar(String email) {
        return familias.criarConta(email, "Pessoa", Consentimentos.VERSAO_DO_AVISO);
    }
}
