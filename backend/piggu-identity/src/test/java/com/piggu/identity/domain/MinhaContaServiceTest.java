package com.piggu.identity.domain;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.BusinessException;
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

    @Autowired
    private com.piggu.identity.avisos.Aparelhos aparelhos;

    @Autowired
    private com.piggu.identity.avisos.AvisosEnviados avisosEnviados;

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

    @Test
    @DisplayName("CPF fica preso a conta: guardado cifrado, sai inteiro so na exportacao")
    void cpfInformado() {
        assertThat(contas.informarCpf(titular.getId(), "529.982.247-25").cpfInformado()).isTrue();

        UserAccount salvo = usuarios.findById(titular.getId()).orElseThrow();
        assertThat(salvo.temCpf()).isTrue();
        assertThat(salvo.getCpfCifrado()).doesNotContain("52998224725");
        assertThat(contas.exportar(como(salvo), "token")).extractingByKey("cpf")
                .asString().contains("529.982.247-25");
    }

    @Test
    @DisplayName("o mesmo CPF nao entra em outra conta, nem com outra pontuacao")
    void cpfUnico() {
        contas.informarCpf(titular.getId(), "529.982.247-25");

        assertThatThrownBy(() -> contas.informarCpf(membro.getId(), "52998224725"))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo(MinhaContaService.CODIGO_CPF_EM_USO));
        assertThat(usuarios.findById(membro.getId()).orElseThrow().temCpf()).isFalse();
    }

    @Test
    @DisplayName("CPF invalido e recusado e CPF ja informado nao muda pelo app")
    void cpfInvalidoOuTroca() {
        assertThatThrownBy(() -> contas.informarCpf(titular.getId(), "111.111.111-11"))
                .isInstanceOf(BusinessException.class);
        contas.informarCpf(titular.getId(), "529.982.247-25");
        assertThatThrownBy(() -> contas.informarCpf(titular.getId(), "111.444.777-35"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("já tem um CPF");
    }

    @Test
    @DisplayName("conta apagada libera o CPF para uma conta nova")
    void exclusaoLiberaCpf() {
        UserAccount sozinho = familias.criarConta("caio@sozinho.test", "Caio", Consentimentos.VERSAO_DO_AVISO);
        contas.informarCpf(sozinho.getId(), "111.444.777-35");
        contas.excluir(sozinho.getId());

        assertThat(contas.informarCpf(titular.getId(), "111.444.777-35").cpfInformado()).isTrue();
    }

    @Test
    @DisplayName("a exportacao leva os aparelhos que recebem avisos e os avisos ja enviados, sem o token inteiro")
    void exportaAvisos() {
        String token = "ab".repeat(32);
        aparelhos.registrar(titular.getId(), token, "IOS");
        avisosEnviados.marcar(titular.getId(), java.util.List.of("fatura:2026-10"));

        java.util.Map<String, Object> arquivo = contas.exportar(como(titular), "token");

        assertThat(arquivo.get("aparelhosQueRecebemAvisos")).asString().contains("IOS", "...ababab")
                .doesNotContain(token);
        assertThat(arquivo.get("avisosEnviados")).asString().contains("fatura:2026-10");
    }

    private static CurrentUser como(UserAccount conta) {
        return new CurrentUser(conta.getId(), conta.getEmail(), conta.getRole(), null, conta.getHouseholdId());
    }
}
