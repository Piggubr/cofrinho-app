package com.piggu.finance.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.security.FamiliaAtual;
import com.piggu.finance.integration.FamiliaNoIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Quem o finance reconhece como da familia, para o dividirCom (BUG-022, D-192). Sem banco. */
class PessoasDaFamiliaTest {

    private static final String INSERIR = "INSERT INTO household_people (household_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FamiliaNoIdentity identity = mock(FamiliaNoIdentity.class);
    private final PessoasDaFamilia pessoas = new PessoasDaFamilia(jdbc, identity);
    private final UUID familia = UUID.randomUUID();
    private final UUID pessoa = UUID.randomUUID();

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private void comToken(String claimFamilia) {
        Jwt.Builder token = Jwt.withTokenValue("t").header("alg", "none").subject(pessoa.toString());
        if (claimFamilia != null) {
            token.claim("familia", claimFamilia);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token.build()));
    }

    @Test
    @DisplayName("chamada com token grava a pessoa na familia do token uma vez so por processo")
    void gravaUmaVez() {
        comToken(familia.toString());

        assertThat(pessoas.preHandle(null, null, null)).isTrue();
        pessoas.preHandle(null, null, null);

        verify(jdbc, times(1)).update(INSERIR, familia, pessoa);
    }

    @Test
    @DisplayName("sem token, ou com token sem familia, nada e gravado")
    void semFamiliaNaoGrava() {
        assertThat(pessoas.preHandle(null, null, null)).isTrue();
        comToken(null);
        pessoas.preHandle(null, null, null);

        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("falha ao gravar nao marca a pessoa como gravada: a proxima chamada tenta de novo")
    void falhaTentaDeNovo() {
        when(jdbc.update(eq(INSERIR), any(Object.class), any(Object.class)))
                .thenThrow(new IllegalStateException("banco fora")).thenReturn(1);

        assertThatThrownBy(() -> pessoas.registrar(familia, pessoa)).isInstanceOf(IllegalStateException.class);
        pessoas.registrar(familia, pessoa);

        verify(jdbc, times(2)).update(INSERIR, familia, pessoa);
    }

    @Test
    @DisplayName("a memoria do que ja foi gravado tem teto: passando dele, recomeca")
    void memoriaComTeto() {
        pessoas.registrar(familia, pessoa);
        for (int i = 0; i <= 50_001; i++) {
            pessoas.registrar(familia, UUID.randomUUID());
        }
        pessoas.registrar(familia, pessoa);

        verify(jdbc, times(2)).update(INSERIR, familia, pessoa);
    }

    @Test
    @DisplayName("todos ja vistos: nem consulta o identity; lista vazia nem consulta o banco")
    void caminhoComum() {
        UUID conhecida = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), eq(UUID.class), any(), any())).thenReturn(List.of(conhecida));

        FamiliaAtual.como(familia, () -> pessoas.exigirDaFamilia(List.of(conhecida, conhecida)));
        pessoas.exigirDaFamilia(List.of());

        verify(jdbc, times(1)).queryForList(anyString(), eq(UUID.class), any(), any());
        verifyNoInteractions(identity);
    }

    @Test
    @DisplayName("parceiro que nunca abriu o app: o identity confirma, ele e gravado e a divisao segue")
    void parceiroNovoConfirmadoNoIdentity() {
        UUID conhecida = UUID.randomUUID();
        UUID parceiro = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), eq(UUID.class), any(), any())).thenReturn(List.of(conhecida));
        when(identity.membros()).thenReturn(new java.util.HashSet<>(List.of(conhecida, parceiro)));

        FamiliaAtual.como(familia, () ->
                assertThatCode(() -> pessoas.exigirDaFamilia(List.of(conhecida, parceiro))).doesNotThrowAnyException());

        verify(jdbc).update(INSERIR, familia, parceiro);
        verify(jdbc).update(INSERIR, familia, conhecida);
    }

    @Test
    @DisplayName("id que nem o identity confirma da 400; identity fora propaga o 503 sem gravar ninguem")
    void naoConfirmado() {
        UUID estranha = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), eq(UUID.class), any(), any())).thenReturn(List.of());
        when(identity.membros()).thenReturn(new java.util.HashSet<>())
                .thenThrow(new BusinessException("fora", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "SERVICO_INDISPONIVEL"));

        FamiliaAtual.como(familia, () -> {
            assertThatThrownBy(() -> pessoas.exigirDaFamilia(List.of(estranha)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Só dá para dividir com pessoas da sua família.")
                    .extracting("codigo").isEqualTo("VALIDACAO");
            assertThatThrownBy(() -> pessoas.exigirDaFamilia(List.of(estranha)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("codigo").isEqualTo("SERVICO_INDISPONIVEL");
        });
        verify(jdbc, never()).update(anyString(), any(Object.class), any(Object.class));
    }
}
