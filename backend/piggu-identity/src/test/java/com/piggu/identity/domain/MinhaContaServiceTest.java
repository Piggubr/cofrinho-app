package com.piggu.identity.domain;

import com.piggu.identity.api.dto.UserResponse;

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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private CofreDeCpf cofre;

    @Autowired
    private com.piggu.identity.billing.AssinaturaService assinaturas;

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
        familias.aceitarParceria(como(parceiro));

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
    @DisplayName("foto propria aparece no lugar da do Google, para a pessoa e para a familia")
    void fotoPropria() {
        titular.atualizarPerfilGoogle("Ana", "Ana", "https://lh3.googleusercontent.com/ana");
        usuarios.save(titular);

        var resposta = contas.trocarFoto(titular.getId(), pngVermelho(), "image/png");

        assertThat(resposta.foto()).startsWith("data:image/jpeg;base64,");
        assertThat(resposta.fotoPropria()).isTrue();
        assertThat(resposta.temFotoDoGoogle()).isTrue();
        assertThat(familias.ver(como(membro)).membros())
                .filteredOn(pessoa -> pessoa.id().equals(titular.getId()))
                .singleElement()
                .satisfies(pessoa -> assertThat(pessoa.foto()).isEqualTo(resposta.foto()));
    }

    @Test
    @DisplayName("o login seguinte com Google nao apaga a foto propria")
    void loginMantemFotoPropria() {
        contas.trocarFoto(titular.getId(), pngVermelho(), "image/png");
        UserAccount recarregada = usuarios.findById(titular.getId()).orElseThrow();
        recarregada.atualizarPerfilGoogle("Ana", "Ana", "https://lh3.googleusercontent.com/nova");
        usuarios.save(recarregada);

        assertThat(usuarios.findById(titular.getId()).orElseThrow().fotoExibida()).startsWith("data:image/jpeg");
    }

    @Test
    @DisplayName("remover tira tudo; voltar ao Google mostra a do Google de novo")
    void removerEVoltarAoGoogle() {
        titular.atualizarPerfilGoogle("Ana", "Ana", "https://lh3.googleusercontent.com/ana");
        usuarios.save(titular);
        contas.trocarFoto(titular.getId(), pngVermelho(), "image/png");

        var semFoto = contas.removerFoto(titular.getId());
        assertThat(semFoto.foto()).isNull();
        assertThat(semFoto.fotoPropria()).isFalse();
        assertThat(usuarios.findById(titular.getId()).orElseThrow().temFotoPropria()).isFalse();

        var doGoogle = contas.usarFotoDoGoogle(titular.getId());
        assertThat(doGoogle.foto()).isEqualTo("https://lh3.googleusercontent.com/ana");
    }

    @Test
    @DisplayName("arquivo que nao e imagem nao muda a foto")
    void fotoInvalida() {
        assertThatThrownBy(() -> contas.trocarFoto(titular.getId(), "bm9wZQ==", "image/png"))
                .isInstanceOf(BusinessException.class);
        assertThat(usuarios.findById(titular.getId()).orElseThrow().temFotoPropria()).isFalse();
    }

    private static String pngVermelho() {
        try {
            java.awt.image.BufferedImage imagem = new java.awt.image.BufferedImage(40, 40,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream saida = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(imagem, "png", saida);
            return java.util.Base64.getEncoder().encodeToString(saida.toByteArray());
        } catch (java.io.IOException erro) {
            throw new IllegalStateException(erro);
        }
    }

    @Test
    @DisplayName("conta sem Stripe nao chama o provedor")
    void semStripe() {
        contas.excluir(membro.getId());
        verify(pagamentos, never()).encerrarCliente(anyString());
    }

    @Test
    @DisplayName("excluir conta que testou guarda so o HMAC do CPF: a conta nova com o mesmo CPF nao testa de novo")
    void excluirGuardaCpfDoTeste() {
        jdbc.update("DELETE FROM trial_cpf_history");
        UserAccount testou = usuarios.findById(membro.getId()).orElseThrow();
        testou.informarCpf("cifrado", "indice-que-testou");
        testou.marcarTesteGratisUsado();
        usuarios.save(testou);

        contas.excluir(membro.getId());

        assertThat(jdbc.queryForList("SELECT * FROM trial_cpf_history"))
                .singleElement().satisfies(linha -> assertThat(linha).containsOnlyKeys("cpf_indice", "used_at")
                        .containsEntry("cpf_indice", "indice-que-testou"));
        UserAccount nova = familias.criarConta("bia2@familia.test", "Bia", Consentimentos.VERSAO_DO_AVISO);
        nova.informarCpf("cifrado", "indice-que-testou");
        usuarios.save(nova);
        assertThat(assinaturas.plano(nova.getId()).diasDeTeste()).isZero();
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
        List<Jwt> enviados = tokensEnviados();
        assertThat(enviados).extracting(t -> t.getClaimAsString("exclusao")).containsExactly("MUDAR", "FAMILIA");
        assertThat(enviados.get(0).getClaimAsString("destino")).as("leva o que e dele para a familia nova")
                .isEqualTo(titular.getHouseholdId().toString());
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

    private List<Jwt> tokensEnviados() {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(cascata, atLeastOnce()).apagar(token.capture());
        return token.getAllValues().stream().map(decoder::decode).toList();
    }


    @Test
    @DisplayName("conta antiga sem aceite aceita a versao vigente dos termos; versao velha e recusada")
    void reaceiteDosTermos() {
        titular.aceitarTermos("2026-09");
        usuarios.save(titular);
        assertThat(contas.exportar(como(titular), "token")).extractingByKey("conta")
                .extracting("termosAceitos").isEqualTo("2026-09");

        assertThatThrownBy(() -> contas.aceitarTermos(titular.getId(), "2026-09"))
                .isInstanceOf(BusinessException.class)
                .extracting("codigo").isEqualTo(Consentimentos.CODIGO_AVISO_MUDOU);

        assertThat(contas.aceitarTermos(titular.getId(), Consentimentos.VERSAO_DO_AVISO).termosAceitos())
                .isEqualTo(Consentimentos.VERSAO_DO_AVISO);
        UserAccount salvo = usuarios.findById(titular.getId()).orElseThrow();
        assertThat(salvo.getTermsVersion()).isEqualTo(Consentimentos.VERSAO_DO_AVISO);
        assertThat(salvo.getTermsAcceptedAt()).isNotNull();
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
    @DisplayName("o CPF e pedido em qualquer pais; o que ja foi informado fica e vale ao mudar")
    void cpfEmQualquerPais() {
        assertThat(contas.informarPais(titular.getId(), "BR", null).cpfInformado()).as("no Brasil, sem CPF").isFalse();

        UserResponse eua = contas.informarPais(titular.getId(), "us", null);
        assertThat(eua.pais()).isEqualTo("US");
        assertThat(eua.cpfInformado()).as("nos EUA tambem pede CPF").isFalse();

        assertThatThrownBy(() -> contas.informarPais(titular.getId(), "ES", "12345678Z"))
                .as("documento de outro pais nao serve mais")
                .isInstanceOf(BusinessException.class).hasMessageContaining("CPF");
        assertThat(contas.informarPais(titular.getId(), "ES", "529.982.247-25").cpfInformado()).isTrue();
        assertThat(usuarios.findById(titular.getId()).orElseThrow().temCpf()).isTrue();

        assertThat(contas.informarPais(titular.getId(), "BR", null).cpfInformado())
                .as("quem ja tem CPF nao precisa de outro ao voltar").isTrue();
    }

    @Test
    @DisplayName("conta antiga com documento de outro pais fica em dia e o exporta")
    void documentoAntigoDeOutroPais() {
        jdbc.update("UPDATE users SET country = 'ES', id_doc_type = 'ES_DNI', id_doc_cifrado = ?,"
                        + " id_doc_indice = 'indice-antigo', id_doc_informado_em = now() WHERE id = ?",
                cofre.cifrar("12345678Z"), titular.getId());

        UserAccount salvo = usuarios.findById(titular.getId()).orElseThrow();
        assertThat(contas.informarPais(titular.getId(), "ES", null).cpfInformado()).isTrue();
        assertThat(contas.exportar(como(salvo), "token")).extractingByKey("documento")
                .asString().contains("12345678Z", "ES_DNI");
    }

    @Test
    @DisplayName("um CPF por conta: o app recusa o CPF de outra conta e o banco tambem (V35)")
    void cpfRepetido() {
        contas.informarCpf(titular.getId(), "529.982.247-25");

        assertThatThrownBy(() -> contas.informarCpf(membro.getId(), "52998224725"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("já está em outra conta");
        assertThat(usuarios.findById(membro.getId()).orElseThrow().temCpf()).isFalse();

        // Dois cadastros ao mesmo tempo passariam pela conferencia: quem barra e o indice unico.
        UserAccount outra = usuarios.findById(membro.getId()).orElseThrow();
        outra.informarCpf("cifrado", usuarios.findById(titular.getId()).orElseThrow().getCpfIndice());
        assertThatThrownBy(() -> usuarios.saveAndFlush(outra))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
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
