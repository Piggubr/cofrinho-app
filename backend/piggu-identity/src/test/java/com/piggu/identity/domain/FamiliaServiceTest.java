package com.piggu.identity.domain;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.identity.api.dto.FamiliaResponse;
import com.piggu.identity.api.dto.UserResponse;
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
    private AuthService auth;

    @Autowired
    private UserAccountRepository usuarios;

    @Autowired
    private HouseholdInviteRepository convites;

    @Autowired
    private RefreshSessionRepository sessoes;

    @Autowired
    private TrilhaDeAuditoria trilha;

    @Autowired
    private HouseholdRepository casas;

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
        membro = entrar("membro@familia.test", "Bia");
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
    @DisplayName("convidado que cria conta nasce na propria familia: so entra na que convidou se aceitar")
    void conviteNaoEntraSozinho() {
        familias.convidar(como(titular), "nova@familia.test");

        UserAccount nova = familias.criarConta("nova@familia.test", "Duda", Consentimentos.VERSAO_DO_AVISO);

        assertThat(nova.getHouseholdId()).isNotEqualTo(titular.getHouseholdId());
        assertThat(nova.getRole()).isEqualTo(PigguRole.TITULAR);
        assertThat(familias.ver(como(titular)).membros()).extracting(FamiliaResponse.Membro::email)
                .doesNotContain("nova@familia.test");
        assertThat(familias.convitesParaMim(como(nova)))
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
    @DisplayName("titular convida o membro para parceiro; so vira parceiro quando aceita, e volta atras na hora")
    void promoverAParceiro() {
        sessoes.save(new RefreshSession(membro.getId(), "hash-do-membro", null, Instant.now().plusSeconds(3600)));

        FamiliaResponse convidado = familias.mudarPapel(como(titular), membro.getId(), PigguRole.PARCEIRO);

        assertThat(usuarios.findById(membro.getId()).orElseThrow().getRole())
                .as("sem aceitar, ninguem passa a ver o que a pessoa lanca").isEqualTo(PigguRole.MEMBRO);
        assertThat(convidado.membros()).filteredOn(pessoa -> pessoa.id().equals(membro.getId()))
                .extracting(FamiliaResponse.Membro::parceriaPendente).containsExactly(true);
        assertThat(sessoes.count()).isEqualTo(1);

        familias.aceitarParceria(como(membro));

        UserAccount parceiro = usuarios.findById(membro.getId()).orElseThrow();
        assertThat(parceiro.getRole()).isEqualTo(PigguRole.PARCEIRO);
        assertThat(parceiro.convidadoParaParceria()).isFalse();
        assertThat(sessoes.count()).as("o token antigo ainda diz MEMBRO").isZero();

        familias.mudarPapel(como(titular), membro.getId(), PigguRole.MEMBRO);
        assertThat(usuarios.findById(membro.getId()).orElseThrow().getRole()).isEqualTo(PigguRole.MEMBRO);
    }

    @Test
    @DisplayName("mostrar tudo ao parceiro vale para aquela pessoa e acaba quando a parceria acaba")
    void mostrarTudoAoParceiro() {
        assertThatThrownBy(() -> auth.escolherCompartilhamento(titular.getId(), true, false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("parceiro");

        familias.mudarPapel(como(titular), membro.getId(), PigguRole.PARCEIRO);
        familias.aceitarParceria(como(membro));
        UserResponse escolha = auth.escolherCompartilhamento(titular.getId(), true, true);

        assertThat(escolha.compartilhamento().mostraTudoAoParceiro()).isTrue();
        assertThat(escolha.compartilhamento().compartilharPorPadrao()).isTrue();
        assertThat(usuarios.findById(titular.getId()).orElseThrow().mostraTudoPara(membro.getId())).isTrue();

        familias.mudarPapel(como(titular), membro.getId(), PigguRole.MEMBRO);

        UserAccount depois = usuarios.findById(titular.getId()).orElseThrow();
        assertThat(depois.getShowsAllTo()).as("quem deixou de ser parceiro nao ve mais tudo").isNull();
        assertThat(depois.isSharesByDefault()).isTrue();
    }

    @Test
    @DisplayName("quem e convidado para parceiro pode recusar; o titular pode desistir do convite")
    void recusarOuCancelarParceria() {
        familias.mudarPapel(como(titular), membro.getId(), PigguRole.PARCEIRO);
        familias.recusarParceria(como(membro));
        assertThat(usuarios.findById(membro.getId()).orElseThrow().convidadoParaParceria()).isFalse();
        assertThatThrownBy(() -> familias.aceitarParceria(como(membro))).isInstanceOf(NotFoundException.class);

        familias.mudarPapel(como(titular), membro.getId(), PigguRole.PARCEIRO);
        familias.mudarPapel(como(titular), membro.getId(), PigguRole.MEMBRO);
        assertThat(usuarios.findById(membro.getId()).orElseThrow().convidadoParaParceria()).isFalse();
        assertThat(usuarios.findById(membro.getId()).orElseThrow().getRole()).isEqualTo(PigguRole.MEMBRO);
    }

    @Test
    @DisplayName("um parceiro por familia: com parceiro ou convite pendente, outro convite e recusado")
    void umParceiroPorFamilia() {
        familias.convidar(como(titular), "caio@familia.test");
        UserAccount caio = entrar("caio@familia.test", "Caio");
        familias.mudarPapel(como(titular), membro.getId(), PigguRole.PARCEIRO);

        assertThatThrownBy(() -> familias.mudarPapel(como(titular), caio.getId(), PigguRole.PARCEIRO))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ja tem um parceiro");

        familias.aceitarParceria(como(membro));
        assertThatThrownBy(() -> familias.mudarPapel(como(titular), caio.getId(), PigguRole.PARCEIRO))
                .isInstanceOf(BusinessException.class);
        assertThat(usuarios.findById(caio.getId()).orElseThrow().convidadoParaParceria()).isFalse();
    }

    @Test
    @DisplayName("parceiro nao convida, nao remove nem muda papel; ninguem vira titular ou admin por aqui")
    void limitesDoParceiro() {
        familias.convidar(como(titular), "caio@familia.test");
        UserAccount terceiro = entrar("caio@familia.test", "Caio");
        familias.mudarPapel(como(titular), membro.getId(), PigguRole.PARCEIRO);
        familias.aceitarParceria(como(membro));
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
        UserAccount outro = entrar("caio@familia.test", "Caio");
        familias.mudarPapel(como(titular), membro.getId(), PigguRole.PARCEIRO);
        familias.aceitarParceria(como(membro));
        familias.removerMembro(como(titular), membro.getId());

        familias.mudarPapel(como(titular), outro.getId(), PigguRole.PARCEIRO);
        familias.aceitarParceria(como(outro));
        familias.sair(como(usuarios.findById(outro.getId()).orElseThrow()));

        assertThat(familias.ver(como(titular)).membros()).hasSize(1);
    }

    @Test
    @DisplayName("convidar para parceiro, aceitar, remover e sair ficam no historico da familia, com quem fez")
    void historicoDaFamilia() {
        familias.convidar(como(titular), "caio@familia.test");
        UserAccount caio = entrar("caio@familia.test", "Caio");

        familias.mudarPapel(como(titular), membro.getId(), PigguRole.PARCEIRO);
        familias.aceitarParceria(como(membro));
        familias.removerMembro(como(titular), caio.getId());
        familias.sair(como(usuarios.findById(membro.getId()).orElseThrow()));

        assertThat(trilha.recentes(titular.getHouseholdId(), "pessoa", 10))
                .extracting(TrilhaDeAuditoria.Evento::acao, TrilhaDeAuditoria.Evento::autor,
                        TrilhaDeAuditoria.Evento::antes, TrilhaDeAuditoria.Evento::depois)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(TrilhaDeAuditoria.Acao.SAIU, membro.getId(),
                                "membro · PARCEIRO", null),
                        org.assertj.core.groups.Tuple.tuple(TrilhaDeAuditoria.Acao.REMOVEU, titular.getId(),
                                "caio · MEMBRO", null),
                        // Quem aceitou ja saiu: o que ela fez fica na familia, sem autor.
                        org.assertj.core.groups.Tuple.tuple(TrilhaDeAuditoria.Acao.MUDOU_PAPEL, null,
                                "membro · MEMBRO", "membro · PARCEIRO"),
                        org.assertj.core.groups.Tuple.tuple(TrilhaDeAuditoria.Acao.CONVIDOU, titular.getId(),
                                null, "membro · PARCEIRO"),
                        // Os dois entraram aceitando o convite e ja sairam: sem autor.
                        org.assertj.core.groups.Tuple.tuple(TrilhaDeAuditoria.Acao.ENTROU, null, null, "caio · MEMBRO"),
                        org.assertj.core.groups.Tuple.tuple(TrilhaDeAuditoria.Acao.ENTROU, null, null,
                                "membro · MEMBRO"));
        assertThat(trilha.recentes(vizinho.getHouseholdId(), null, 10)).isEmpty();
    }

    @Test
    @DisplayName("quem paga o Premium da propria familia so aceita convite depois que ele termina")
    void assinanteNaoLevaOPlano() {
        Household daCaio = casas.findById(vizinho.getHouseholdId()).orElseThrow();
        Instant agora = Instant.now();
        daCaio.aplicarAssinatura("WEB", agora.plus(java.time.Duration.ofDays(30)), agora, agora,
                com.piggu.identity.billing.TipoDePlano.INDIVIDUAL, vizinho.getId());
        casas.save(daCaio);
        familias.convidar(como(titular), "vizinho@outra.test");
        java.util.UUID convite = familias.convitesParaMim(como(vizinho)).get(0).id();

        assertThatThrownBy(() -> familias.aceitarConvite(como(vizinho), convite))
                .isInstanceOf(BusinessException.class).extracting("codigo").isEqualTo("ASSINATURA_ATIVA");

        daCaio = casas.findById(vizinho.getHouseholdId()).orElseThrow();
        daCaio.aplicarAssinatura("WEB", agora.minusSeconds(1), agora.plusSeconds(1));
        casas.save(daCaio);
        familias.aceitarConvite(como(vizinho), convite);
        assertThat(usuarios.findById(vizinho.getId()).orElseThrow().getHouseholdId()).isEqualTo(titular.getHouseholdId());
    }

    @Test
    @DisplayName("no plano casal o primeiro convidado entra como parceiro, o seguinte como membro, e menor de idade volta a membro")
    void parceiroNoPlanoCasal() {
        Household daAna = casas.findById(titular.getHouseholdId()).orElseThrow();
        Instant agora = Instant.now();
        daAna.aplicarAssinatura("WEB", agora.plus(java.time.Duration.ofDays(30)), agora, agora,
                com.piggu.identity.billing.TipoDePlano.CASAL, titular.getId());
        casas.save(daAna);

        familias.convidar(como(titular), "marido@familia.test");
        familias.convidar(como(titular), "filho@familia.test");
        UserAccount marido = entrar("marido@familia.test", "Rui");
        UserAccount filho = entrar("filho@familia.test", "Leo");

        assertThat(marido.getRole()).isEqualTo(PigguRole.PARCEIRO);
        assertThat(filho.getRole()).isEqualTo(PigguRole.MEMBRO);
        assertThat(membro.getRole()).isEqualTo(PigguRole.MEMBRO);

        marido.informarNascimento(java.time.LocalDate.now().minusYears(15));
        assertThat(marido.getRole()).isEqualTo(PigguRole.MEMBRO);
    }

    @Test
    @DisplayName("o titular que foi para outra familia nao age mais como titular com o token antigo (D-158)")
    void tokenAntigoDeQuemSaiu() {
        CurrentUser antigo = como(titular);
        familias.convidar(como(vizinho), "titular@familia.test");
        familias.convidar(como(vizinho), "pendente@outra.test");
        java.util.UUID convite = familias.convitesParaMim(como(titular)).get(0).id();
        familias.aceitarConvite(antigo, convite);

        assertThatThrownBy(() -> familias.removerMembro(antigo, membro.getId()))
                .isInstanceOf(com.piggu.common.error.ForbiddenException.class);
        assertThatThrownBy(() -> familias.renomear(antigo, "Outra"))
                .isInstanceOf(com.piggu.common.error.ForbiddenException.class);
        assertThat(usuarios.findById(membro.getId()).orElseThrow().getHouseholdId())
                .isNotEqualTo(vizinho.getHouseholdId());
        assertThat(familias.ver(antigo).convites()).isEmpty();
    }

    @Test
    @DisplayName("convite vencido renovado passa pelo limite de 5 do plano familia (D-159)")
    void conviteVencidoNoLimiteDoFamilia() {
        Household casa = casas.findById(titular.getHouseholdId()).orElseThrow();
        Instant agora = Instant.now();
        casa.aplicarAssinatura("WEB", agora.plus(java.time.Duration.ofDays(30)), agora, agora,
                com.piggu.identity.billing.TipoDePlano.FAMILIA, titular.getId());
        casas.save(casa);
        familias.convidar(como(titular), "c@familia.test");
        familias.convidar(como(titular), "d@familia.test");
        familias.convidar(como(titular), "e@familia.test");
        entrar("c@familia.test", "C");
        entrar("d@familia.test", "D");
        entrar("e@familia.test", "E");
        convites.save(new HouseholdInvite(titular.getHouseholdId(), "f@familia.test", titular.getId(),
                agora.minusSeconds(60)));

        assertThatThrownBy(() -> familias.convidar(como(titular), "f@familia.test"))
                .isInstanceOf(BusinessException.class).extracting("codigo").isEqualTo("LIMITE_DO_FAMILIA");
    }

    private UserAccount entrar(String email, String nome) {
        return Convidados.entrar(familias, convites, usuarios, email, nome);
    }

    private static CurrentUser como(UserAccount conta) {
        return new CurrentUser(conta.getId(), conta.getEmail(), conta.getRole(), null, conta.getHouseholdId());
    }
}
