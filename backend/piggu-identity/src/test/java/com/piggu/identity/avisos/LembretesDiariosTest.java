package com.piggu.identity.avisos;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.security.CurrentUser;
import com.piggu.identity.domain.AuthService;
import com.piggu.identity.domain.FamiliaService;
import com.piggu.identity.domain.HouseholdInviteRepository;
import com.piggu.identity.domain.RefreshSessionRepository;
import com.piggu.identity.domain.UserAccount;
import com.piggu.identity.domain.UserAccountRepository;
import com.piggu.testing.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lembrete diario: quem recebe, por onde, e que o mesmo aviso nao chega duas vezes. */
class LembretesDiariosTest extends PostgresIntegrationTest {

    @MockitoBean
    private AvisosDoFinanceiro financeiro;

    @MockitoBean
    private CanalDeEmail email;

    @MockitoBean
    private CanalDePush push;

    @Autowired
    private LembretesDiarios lembretes;

    @Autowired
    private FamiliaService familias;

    @Autowired
    private AuthService auth;

    @Autowired
    private Aparelhos aparelhos;

    @Autowired
    private UserAccountRepository usuarios;

    @Autowired
    private HouseholdInviteRepository convites;

    @Autowired
    private RefreshSessionRepository sessoes;

    @Autowired
    private JwtDecoder decoder;

    private UserAccount ana;

    @BeforeEach
    void preparar() {
        sessoes.deleteAll();
        convites.deleteAll();
        usuarios.deleteAll();
        ana = familias.criarConta("ana@familia.test", "Ana", Consentimentos.VERSAO_DO_AVISO);
        when(financeiro.ligado()).thenReturn(true);
        when(email.ligado()).thenReturn(true);
        when(push.ligado()).thenReturn(true);
        when(email.enviar(anyString(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("manda por e-mail o que e novo, com o token da propria pessoa, e nao repete no dia seguinte")
    void naoRepete() {
        auth.escolherAvisos(ana.getId(), true, false);
        when(financeiro.deHoje(anyString())).thenReturn(List.of(fatura("f1")));

        assertThat(lembretes.enviarDeHoje()).isEqualTo(1);
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(financeiro).deHoje(token.capture());
        assertThat(decoder.decode(token.getValue()).getSubject()).isEqualTo(ana.getId().toString());
        ArgumentCaptor<Mensagem> mensagem = ArgumentCaptor.forClass(Mensagem.class);
        verify(email).enviar(eq("ana@familia.test"), mensagem.capture());
        assertThat(mensagem.getValue().linhas()).singleElement().asString().startsWith("Fatura do Nubank vence em 23/10");

        when(financeiro.deHoje(anyString())).thenReturn(List.of(fatura("f1"), fatura("f2")));
        assertThat(lembretes.enviarDeHoje()).isEqualTo(1);
        verify(email, times(2)).enviar(eq("ana@familia.test"), mensagem.capture());
        assertThat(mensagem.getValue().linhas()).hasSize(1);

        assertThat(lembretes.enviarDeHoje()).isZero();
        verify(email, times(2)).enviar(anyString(), any());
    }

    @Test
    @DisplayName("entrega que falha nao marca: o aviso tenta de novo depois")
    void falhaNaoMarca() {
        auth.escolherAvisos(ana.getId(), true, false);
        when(financeiro.deHoje(anyString())).thenReturn(List.of(fatura("f1")));
        when(email.enviar(anyString(), any())).thenReturn(false);

        assertThat(lembretes.enviarDeHoje()).isZero();
        when(email.enviar(anyString(), any())).thenReturn(true);
        assertThat(lembretes.enviarDeHoje()).isEqualTo(1);
    }

    @Test
    @DisplayName("push vai para cada aparelho; o que a Apple recusa e esquecido; quem nao ligou nada nem e consultado")
    void push() {
        UserAccount caio = familias.criarConta("caio@outra.test", "Caio", Consentimentos.VERSAO_DO_AVISO);
        auth.escolherAvisos(ana.getId(), false, true);
        aparelhos.registrar(ana.getId(), "aa".repeat(32), "IOS");
        aparelhos.registrar(ana.getId(), "bb".repeat(32), "IOS");
        when(financeiro.deHoje(anyString())).thenReturn(List.of(fatura("f1")));
        when(push.enviar(eq("aa".repeat(32)), any())).thenReturn(CanalDePush.Resultado.ENVIADO);
        when(push.enviar(eq("bb".repeat(32)), any())).thenReturn(CanalDePush.Resultado.TOKEN_INVALIDO);

        assertThat(lembretes.enviarDeHoje()).isEqualTo(1);

        assertThat(aparelhos.doUsuario(ana.getId())).containsExactly("aa".repeat(32));
        verify(email, never()).enviar(anyString(), any());
        verify(financeiro, times(1)).deHoje(anyString());
        assertThat(caio.isAlertsByEmail()).isFalse();
    }

    @Test
    @DisplayName("membro nao recebe avisos de financas, mesmo com o canal ligado")
    void membroFora() {
        familias.convidar(new CurrentUser(ana.getId(), ana.getEmail(), ana.getRole(), null, ana.getHouseholdId()),
                "bia@familia.test");
        UserAccount bia = com.piggu.identity.domain.Convidados.entrar(familias, convites, usuarios, "bia@familia.test", "Bia");
        auth.escolherAvisos(bia.getId(), true, true);

        assertThat(usuarios.comAvisosLigados()).isEmpty();
    }

    private static Aviso fatura(String id) {
        return new Aviso("fatura:" + id, "FATURA_VENCENDO", "Nubank", new BigDecimal("1200.50"), null, null,
                LocalDate.of(2026, 10, 23));
    }
}
