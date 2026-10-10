package com.piggu.identity.domain;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.identity.api.dto.FamiliaResponse;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Convites, remocao e saida da familia. */
class FamiliaServiceTest extends PostgresIntegrationTest {

    @Autowired
    private FamiliaService familias;

    @Autowired
    private UserAccountRepository usuarios;

    @Autowired
    private HouseholdInviteRepository convites;

    @Autowired
    private RefreshSessionRepository sessoes;

    private UserAccount titular;
    private UserAccount membro;
    private UserAccount vizinho;

    @BeforeEach
    void preparar() {
        sessoes.deleteAll();
        convites.deleteAll();
        usuarios.deleteAll();
        titular = familias.criarConta("titular@familia.test", "Ana", Consentimentos.VERSAO_DO_AVISO);
        familias.convidar(como(titular), "membro@familia.test");
        membro = familias.criarConta("membro@familia.test", "Bia", Consentimentos.VERSAO_DO_AVISO);
        vizinho = familias.criarConta("vizinho@outra.test", "Caio", Consentimentos.VERSAO_DO_AVISO);
    }

    @Test
    @DisplayName("a familia mostra os dois e so o titular ve convites pendentes")
    void verFamilia() {
        familias.convidar(como(titular), "pendente@familia.test");

        FamiliaResponse doTitular = familias.ver(como(titular));
        FamiliaResponse doMembro = familias.ver(como(membro));

        assertThat(doTitular.nome()).isEqualTo("Familia de Ana");
        assertThat(doTitular.membros()).extracting(FamiliaResponse.Membro::email)
                .containsExactly("titular@familia.test", "membro@familia.test");
        assertThat(doTitular.convites()).extracting(FamiliaResponse.Convite::email).containsExactly("pendente@familia.test");
        assertThat(doMembro.convites()).isEmpty();
        assertThat(familias.ver(como(vizinho)).membros()).hasSize(1);
    }

    @Test
    @DisplayName("quem ja e da casa nao recebe convite; quem tem conta em outra familia recebe")
    void conviteParaQuemJaTemConta() {
        assertThatThrownBy(() -> familias.convidar(como(titular), "membro@familia.test"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ja faz parte");

        familias.convidar(como(titular), "vizinho@outra.test");
        assertThat(familias.convitesParaMim(como(vizinho)))
                .extracting(FamiliaResponse.Convite::familia).containsExactly("Familia de Ana");
    }

    @Test
    @DisplayName("no maximo 10 convites esperando resposta; renovar um que ja existe continua valendo")
    void limiteDeConvites() {
        for (int i = 0; convites.countByHouseholdIdAndExpiresAtAfter(titular.getHouseholdId(), java.time.Instant.now())
                < FamiliaService.MAXIMO_DE_CONVITES; i++) {
            familias.convidar(como(titular), "pessoa" + i + "@familia.test");
        }
        assertThatThrownBy(() -> familias.convidar(como(titular), "mais-uma@familia.test"))
                .hasMessageContaining("convites esperando resposta");
        familias.convidar(como(titular), "pessoa0@familia.test");
    }

    @Test
    @DisplayName("membro nao convida nem remove; o titular nao cancela convite de outra familia")
    void permissoes() {
        assertThatThrownBy(() -> familias.convidar(como(membro), "x@familia.test")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> familias.removerMembro(como(membro), titular.getId())).isInstanceOf(ForbiddenException.class);

        HouseholdInvite deFora = convites.save(new HouseholdInvite(vizinho.getHouseholdId(), "y@outra.test",
                vizinho.getId(), Instant.now().plusSeconds(3600)));
        assertThatThrownBy(() -> familias.cancelarConvite(como(titular), deFora.getId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> familias.removerMembro(como(titular), vizinho.getId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("membro removido ganha familia propria como titular e perde as sessoes abertas")
    void removerMembro() {
        sessoes.save(new RefreshSession(membro.getId(), "hash-do-membro", null, Instant.now().plusSeconds(3600)));

        familias.removerMembro(como(titular), membro.getId());

        UserAccount depois = usuarios.findById(membro.getId()).orElseThrow();
        assertThat(depois.getHouseholdId()).isNotEqualTo(titular.getHouseholdId());
        assertThat(depois.getRole()).isEqualTo(PigguRole.TITULAR);
        assertThat(sessoes.count()).isZero();
        assertThat(familias.ver(como(titular)).membros()).hasSize(1);
    }

    @Test
    @DisplayName("membro sai sozinho; titular nao sai da propria familia")
    void sair() {
        familias.sair(como(membro));
        assertThat(usuarios.findById(membro.getId()).orElseThrow().getHouseholdId()).isNotEqualTo(titular.getHouseholdId());

        assertThatThrownBy(() -> familias.sair(como(titular))).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("titular promove o membro a parceiro e volta atras; a pessoa entra de novo")
    void promoverAParceiro() {
        sessoes.save(new RefreshSession(membro.getId(), "hash-do-membro", null, Instant.now().plusSeconds(3600)));

        FamiliaResponse depois = familias.mudarPapel(como(titular), membro.getId(), PigguRole.PARCEIRO);

        assertThat(usuarios.findById(membro.getId()).orElseThrow().getRole()).isEqualTo(PigguRole.PARCEIRO);
        assertThat(depois.membros()).extracting(FamiliaResponse.Membro::papel).contains(PigguRole.PARCEIRO);
        assertThat(sessoes.count()).as("o token antigo ainda diz MEMBRO").isZero();

        familias.mudarPapel(como(titular), membro.getId(), PigguRole.MEMBRO);
        assertThat(usuarios.findById(membro.getId()).orElseThrow().getRole()).isEqualTo(PigguRole.MEMBRO);
    }

    @Test
    @DisplayName("parceiro nao convida, nao remove nem muda papel; ninguem vira titular ou admin por aqui")
    void limitesDoParceiro() {
        familias.convidar(como(titular), "caio@familia.test");
        UserAccount terceiro = familias.criarConta("caio@familia.test", "Caio", Consentimentos.VERSAO_DO_AVISO);
        familias.mudarPapel(como(titular), membro.getId(), PigguRole.PARCEIRO);
        UserAccount parceiro = usuarios.findById(membro.getId()).orElseThrow();

        assertThatThrownBy(() -> familias.convidar(como(parceiro), "x@familia.test")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> familias.removerMembro(como(parceiro), terceiro.getId())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> familias.mudarPapel(como(parceiro), terceiro.getId(), PigguRole.PARCEIRO))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> familias.mudarPapel(como(titular), terceiro.getId(), PigguRole.TITULAR))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> familias.mudarPapel(como(titular), terceiro.getId(), PigguRole.ADMIN))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> familias.mudarPapel(como(titular), titular.getId(), PigguRole.MEMBRO))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> familias.mudarPapel(como(titular), vizinho.getId(), PigguRole.PARCEIRO))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("parceiro pode ser removido pelo titular e pode sair sozinho")
    void parceiroSaiOuERemovido() {
        familias.convidar(como(titular), "caio@familia.test");
        UserAccount outro = familias.criarConta("caio@familia.test", "Caio", Consentimentos.VERSAO_DO_AVISO);
        familias.mudarPapel(como(titular), membro.getId(), PigguRole.PARCEIRO);
        familias.mudarPapel(como(titular), outro.getId(), PigguRole.PARCEIRO);

        familias.removerMembro(como(titular), membro.getId());
        familias.sair(como(usuarios.findById(outro.getId()).orElseThrow()));

        assertThat(familias.ver(como(titular)).membros()).hasSize(1);
    }

    private static CurrentUser como(UserAccount conta) {
        return new CurrentUser(conta.getId(), conta.getEmail(), conta.getRole(), null, conta.getHouseholdId());
    }
}
