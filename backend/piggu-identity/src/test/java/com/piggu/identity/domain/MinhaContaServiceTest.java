package com.piggu.identity.domain;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.UpstreamException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.identity.billing.ProvedorDePagamento;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Exclusao de conta e convite aceito por quem ja tem conta.
 *
 * <p>A cascata para os outros servicos e simulada: o que se prova aqui e o escopo que
 * vai no token, a ordem (servicos antes da conta) e o que acontece com a familia.</p>
 */
class MinhaContaServiceTest extends PostgresIntegrationTest {

    @MockitoBean
    private CascataDeDados cascata;

    @MockitoBean
    private ProvedorDePagamento pagamentos;

    @Autowired
    private MinhaContaService contas;

    @Autowired
    private FamiliaService familias;

    @Autowired
    private UserAccountRepository usuarios;

    @Autowired
    private HouseholdRepository casas;

    @Autowired
    private HouseholdInviteRepository convites;

    @Autowired
    private RefreshSessionRepository sessoes;

    @Autowired
    private JwtDecoder decoder;

    private UserAccount titular;
    private UserAccount membro;

    @BeforeEach
    void preparar() {
        sessoes.deleteAll();
        convites.deleteAll();
        usuarios.deleteAll();
        titular = familias.criarConta("ana@familia.test", "Ana", Consentimentos.VERSAO_DO_AVISO);
        familias.convidar(como(titular), "bia@familia.test");
        membro = familias.criarConta("bia@familia.test", "Bia", Consentimentos.VERSAO_DO_AVISO);
    }

    @Test
    @DisplayName("ultima pessoa da familia: escopo FAMILIA, conta e familia somem")
    void ultimaPessoa() {
        UserAccount sozinho = familias.criarConta("caio@sozinho.test", "Caio", Consentimentos.VERSAO_DO_AVISO);

        contas.excluir(sozinho.getId());

        assertThat(escopoEnviado()).isEqualTo("FAMILIA");
        assertThat(usuarios.findById(sozinho.getId())).isEmpty();
        assertThat(casas.findById(sozinho.getHouseholdId())).isEmpty();
    }

    @Test
    @DisplayName("titular sai e a familia fica: escopo PESSOA e o membro vira titular")
    void titularSaiMembroAssume() {
        contas.excluir(titular.getId());

        assertThat(escopoEnviado()).isEqualTo("PESSOA");
        assertThat(usuarios.findById(membro.getId()).orElseThrow().getRole()).isEqualTo(PigguRole.TITULAR);
        assertThat(casas.findById(titular.getHouseholdId())).isPresent();
    }

    @Test
    @DisplayName("titular sai e ha um parceiro: o parceiro assume, mesmo sendo mais novo que o membro")
    void titularSaiParceiroAssume() {
        familias.convidar(como(titular), "caio@familia.test");
        UserAccount parceiro = familias.criarConta("caio@familia.test", "Caio", Consentimentos.VERSAO_DO_AVISO);
        familias.mudarPapel(como(titular), parceiro.getId(), PigguRole.PARCEIRO);

        contas.excluir(titular.getId());

        assertThat(usuarios.findById(parceiro.getId()).orElseThrow().getRole()).isEqualTo(PigguRole.TITULAR);
        assertThat(usuarios.findById(membro.getId()).orElseThrow().getRole()).isEqualTo(PigguRole.MEMBRO);
    }

    @Test
    @DisplayName("servico fora do ar: a conta continua inteira para tentar de novo")
    void falhaNaCascata() {
        willThrow(new UpstreamException("fora do ar")).given(cascata).apagar(anyString());

        assertThatThrownBy(() -> contas.excluir(titular.getId())).isInstanceOf(UpstreamException.class);

        assertThat(usuarios.findById(titular.getId())).isPresent();
        assertThat(usuarios.findById(membro.getId()).orElseThrow().getRole()).isEqualTo(PigguRole.MEMBRO);
    }

    @Test
    @DisplayName("quem pagou pela Stripe tem o cliente encerrado, o que cancela a assinatura")
    void encerraClienteNaStripe() {
        titular.lembrarClienteNoProvedor("cus_123");
        usuarios.save(titular);
        given(pagamentos.habilitado()).willReturn(true);

        contas.excluir(titular.getId());

        verify(pagamentos).encerrarCliente("cus_123");
    }

    @Test
    @DisplayName("conta sem Stripe nao chama o provedor")
    void semStripe() {
        contas.excluir(membro.getId());
        verify(pagamentos, never()).encerrarCliente(anyString());
    }

    @Test
    @DisplayName("quem ja tem conta aceita convite: sai da familia antiga e entra como membro")
    void aceitaConvite() {
        UserAccount vizinho = familias.criarConta("caio@vizinho.test", "Caio", Consentimentos.VERSAO_DO_AVISO);
        familias.convidar(como(titular), "caio@vizinho.test");
        assertThat(familias.convitesParaMim(como(vizinho))).hasSize(1);

        familias.aceitarConvite(como(vizinho), familias.convitesParaMim(como(vizinho)).get(0).id());

        UserAccount depois = usuarios.findById(vizinho.getId()).orElseThrow();
        assertThat(depois.getHouseholdId()).isEqualTo(titular.getHouseholdId());
        assertThat(depois.getRole()).isEqualTo(PigguRole.MEMBRO);
        assertThat(casas.findById(vizinho.getHouseholdId())).as("a familia antiga ficou vazia").isEmpty();
        assertThat(escopoEnviado()).isEqualTo("FAMILIA");
    }

    @Test
    @DisplayName("convite de outra pessoa ou vencido nao e aceito")
    void conviteAlheio() {
        UserAccount vizinho = familias.criarConta("caio@vizinho.test", "Caio", Consentimentos.VERSAO_DO_AVISO);
        HouseholdInvite alheio = convites.save(new HouseholdInvite(titular.getHouseholdId(), "outra@x.test",
                titular.getId(), Instant.now().plusSeconds(60)));

        assertThatThrownBy(() -> familias.aceitarConvite(como(vizinho), alheio.getId()))
                .isInstanceOf(com.piggu.common.error.NotFoundException.class);
    }

    private String escopoEnviado() {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(cascata).apagar(token.capture());
        return decoder.decode(token.getValue()).getClaimAsString("exclusao");
    }

    private static CurrentUser como(UserAccount conta) {
        return new CurrentUser(conta.getId(), conta.getEmail(), conta.getRole(), null, conta.getHouseholdId());
    }
}
