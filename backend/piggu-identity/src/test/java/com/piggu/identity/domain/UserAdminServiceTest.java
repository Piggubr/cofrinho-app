package com.piggu.identity.domain;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.PigguRole;
import com.piggu.identity.api.dto.UpdateUserRequest;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O que o administrador muda numa conta, e a revogacao de sessoes que acompanha.
 *
 * <p>Desativar sem derrubar as sessoes deixaria o token atual valendo ate expirar; e a
 * revogacao precisa sobreviver a uma excecao na transacao de fora (SessionRevoker).</p>
 */
class UserAdminServiceTest extends PostgresIntegrationTest {

    @Autowired
    private UserAdminService admin;

    @Autowired
    private SessionRevoker revogador;

    @Autowired
    private FamiliaService familias;

    @Autowired
    private UserAccountRepository usuarios;

    @Autowired
    private HouseholdInviteRepository convites;

    @Autowired
    private RefreshSessionRepository sessoes;

    @Autowired
    private TrilhaDeAuditoria trilha;

    @Autowired
    private TransactionTemplate transacao;

    private UserAccount ana;
    private UserAccount caio;

    @BeforeEach
    void preparar() {
        sessoes.deleteAll();
        convites.deleteAll();
        usuarios.deleteAll();
        caio = familias.criarConta("caio@familia.test", "Caio", Consentimentos.VERSAO_DO_AVISO);
        ana = familias.criarConta("ana@familia.test", "Ana", Consentimentos.VERSAO_DO_AVISO);
    }

    @Test
    @DisplayName("lista por e-mail, com a familia de cada conta")
    void lista() {
        assertThat(admin.listar()).extracting(UserResponse::email)
                .containsExactly("ana@familia.test", "caio@familia.test");
        assertThat(admin.listar().get(0).familia()).isEqualTo(ana.getHouseholdId());
    }

    @Test
    @DisplayName("campo nulo fica como esta; apelido e cortado no limite")
    void camposNulos() {
        UserResponse depois = admin.alterar(ana.getId(), new UpdateUserRequest("a".repeat(130), null, null));

        assertThat(depois.apelido()).hasSize(120);
        assertThat(depois.role()).isEqualTo(ana.getRole());
        assertThat(depois.ativo()).isTrue();
    }

    @Test
    @DisplayName("mudar o papel fica na trilha da familia da pessoa")
    void papelNaTrilha() {
        PigguRole antes = ana.getRole();

        admin.alterar(ana.getId(), new UpdateUserRequest(null, PigguRole.ADMIN, null));

        assertThat(usuarios.findById(ana.getId()).orElseThrow().getRole()).isEqualTo(PigguRole.ADMIN);
        assertThat(trilha.recentes(ana.getHouseholdId(), "pessoa", 5)).singleElement().satisfies(evento -> {
            assertThat(evento.acao()).isEqualTo(TrilhaDeAuditoria.Acao.MUDOU_PAPEL);
            assertThat(evento.antes()).endsWith(antes.name());
            assertThat(evento.depois()).endsWith("ADMIN");
        });
        assertThat(trilha.recentes(caio.getHouseholdId(), "pessoa", 5)).isEmpty();
    }

    @Test
    @DisplayName("mandar o mesmo papel nao gera evento")
    void mesmoPapel() {
        admin.alterar(ana.getId(), new UpdateUserRequest(null, ana.getRole(), null));

        assertThat(trilha.recentes(ana.getHouseholdId(), "pessoa", 5)).isEmpty();
    }

    @Test
    @DisplayName("desativar derruba as sessoes so daquela conta; reativar nao mexe nelas")
    void desativarDerrubaSessoes() {
        abrirSessao(ana);
        abrirSessao(ana);
        abrirSessao(caio);

        UserResponse depois = admin.alterar(ana.getId(), new UpdateUserRequest(null, null, false));

        assertThat(depois.ativo()).isFalse();
        assertThat(sessoes.findAll()).extracting(RefreshSession::getUserId).containsExactly(caio.getId());

        abrirSessao(ana);
        admin.alterar(ana.getId(), new UpdateUserRequest(null, null, true));
        assertThat(sessoes.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("conta que nao existe da 404")
    void naoEncontrada() {
        assertThatThrownBy(() -> admin.alterar(UUID.randomUUID(), new UpdateUserRequest("x", null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("a revogacao confirma mesmo se a transacao de fora for desfeita")
    void revogacaoSobreviveARecusa() {
        abrirSessao(ana);
        RefreshSession outra = abrirSessao(caio);

        assertThatThrownBy(() -> transacao.executeWithoutResult(status -> {
            revogador.revogarTodasDe(ana.getId());
            revogador.revogar(outra.getId());
            throw new IllegalStateException("recusa");
        })).hasMessage("recusa");

        assertThat(sessoes.count()).isZero();
    }

    private RefreshSession abrirSessao(UserAccount conta) {
        return sessoes.save(new RefreshSession(conta.getId(), UUID.randomUUID().toString(), "teste",
                Instant.now().plus(1, ChronoUnit.DAYS)));
    }
}
