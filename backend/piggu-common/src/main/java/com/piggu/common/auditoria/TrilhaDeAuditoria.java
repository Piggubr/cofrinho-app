package com.piggu.common.auditoria;

import com.piggu.common.dados.DadosDaFamilia;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.CurrentUserArgumentResolver;
import com.piggu.common.security.FamiliaAtual;
import com.piggu.common.web.Texto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Quem mudou o que, e quando, nos dados da familia (tabela {@code eventos_de_auditoria}).
 *
 * <p>Responde o "quem apagou esse gasto?" e, num incidente, mostra o que mudou sem
 * depender do log de texto. Cada servico que usa tem a propria tabela (migration) e
 * a declara no {@code DadosDaFamilia}, para o evento sair junto na exportacao e na
 * exclusao da conta.</p>
 *
 * <p>Grava na mesma transacao da mudanca: se a mudanca volta atras, o evento tambem.
 * O resumo e curto e legivel ("Pao · Mercado · 12.50"), nunca o registro inteiro, e o
 * evento vive {@link #RETENCAO_PADRAO} (configuravel).</p>
 */
public class TrilhaDeAuditoria {

    /** Um ano e um mes: cobre a pergunta do mes passado e o fechamento do ano. */
    public static final Duration RETENCAO_PADRAO = Duration.ofDays(400);

    /** Autor de mudanca feita por job agendado (conta fixa automatica, retencao). */
    public static final String SISTEMA = "sistema";

    private static final int LIMITE_DO_RESUMO = 300;
    private static final Logger log = LoggerFactory.getLogger(TrilhaDeAuditoria.class);

    public enum Acao { CRIOU, EDITOU, APAGOU, IMPORTOU, PAGOU, MUDOU_PAPEL, REMOVEU, ENTROU, SAIU, CONVIDOU }

    public record Evento(long id, String autor, Acao acao, String entidade, String entidadeId,
                         String antes, String depois, Instant quando) {
    }

    private final JdbcTemplate jdbc;
    private final Duration retencao;
    private final Clock relogio;

    public TrilhaDeAuditoria(JdbcTemplate jdbc, Duration retencao, Clock relogio) {
        this.jdbc = jdbc;
        this.retencao = retencao;
        this.relogio = relogio;
    }

    public TrilhaDeAuditoria(JdbcTemplate jdbc) {
        this(jdbc, RETENCAO_PADRAO, Clock.systemUTC());
    }

    public void criou(String entidade, Object id, String depois) {
        registrar(Acao.CRIOU, entidade, id, null, depois);
    }

    public void editou(String entidade, Object id, String antes, String depois) {
        if (antes != null && antes.equals(depois)) {
            return;
        }
        registrar(Acao.EDITOU, entidade, id, antes, depois);
    }

    public void apagou(String entidade, Object id, String antes) {
        registrar(Acao.APAGOU, entidade, id, antes, null);
    }

    /** Registra em nome de quem esta na requisicao; sem requisicao, o {@link #SISTEMA}. */
    public void registrar(Acao acao, String entidade, Object id, String antes, String depois) {
        UUID familia = FamiliaAtual.atual();
        if (FamiliaAtual.NENHUMA.equals(familia) || FamiliaAtual.TODAS.equals(familia)) {
            log.warn("Evento de auditoria sem familia ignorado: acao={} entidade={}", acao, entidade);
            return;
        }
        registrar(familia, autorDaRequisicao(), acao, entidade, id, antes, depois);
    }

    /** Para quando a familia ou o autor nao sao os da requisicao (o identity muda a familia da pessoa). */
    public void registrar(UUID familia, String autor, Acao acao, String entidade, Object id, String antes, String depois) {
        jdbc.update("""
                INSERT INTO eventos_de_auditoria
                    (household_id, autor_email, acao, entidade, entidade_id, antes, depois, criado_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                familia, autor, acao.name(), entidade, id == null ? null : id.toString(),
                curto(antes), curto(depois),
                Timestamp.from(relogio.instant()));
    }

    /** Os mais recentes da familia, para a tela de historico. SQL puro: filtra a familia a mao. */
    public List<Evento> recentes(UUID familia, String entidade, int limite) {
        String sql = """
                SELECT id, autor_email, acao, entidade, entidade_id, antes, depois, criado_em
                FROM eventos_de_auditoria WHERE household_id = ?
                """ + (entidade == null ? "" : " AND entidade = ?") + " ORDER BY id DESC LIMIT ?";
        Object[] parametros = entidade == null
                ? new Object[]{familia, limite}
                : new Object[]{familia, entidade, limite};
        return jdbc.query(sql, (linha, n) -> new Evento(
                linha.getLong("id"),
                linha.getString("autor_email"),
                Acao.valueOf(linha.getString("acao")),
                linha.getString("entidade"),
                linha.getString("entidade_id"),
                linha.getString("antes"),
                linha.getString("depois"),
                linha.getTimestamp("criado_em").toInstant()), parametros);
    }

    /**
     * Para o servico que nao usa o {@code DadosDaFamilia} (o identity): a familia sumiu,
     * os eventos dela saem; a pessoa saiu, o nome dela vira {@code DadosDaFamilia.ANONIMO}.
     */
    public void esquecer(UUID familia, String email, boolean familiaInteira) {
        if (familiaInteira) {
            jdbc.update("DELETE FROM eventos_de_auditoria WHERE household_id = ?", familia);
        } else {
            jdbc.update("UPDATE eventos_de_auditoria SET autor_email = ? WHERE household_id = ? AND autor_email = ?",
                    DadosDaFamilia.ANONIMO, familia, email);
        }
    }

    /** Retencao: o evento e dado pessoal (diz quem fez o que); passado o prazo, sai. */
    @Scheduled(cron = "${piggu.auditoria.retencao-cron:0 40 3 * * *}", zone = "America/Sao_Paulo")
    public int apagarAntigos() {
        int apagados = jdbc.update("DELETE FROM eventos_de_auditoria WHERE criado_em < ?",
                Timestamp.from(relogio.instant().minus(retencao)));
        if (apagados > 0) {
            log.info("Retencao da auditoria: eventos apagados={}", apagados);
        }
        return apagados;
    }

    private static String curto(String resumo) {
        return resumo == null ? null : Texto.limitar(resumo, LIMITE_DO_RESUMO);
    }

    /** E-mail de quem esta na requisicao; sem requisicao (job agendado), o {@link #SISTEMA}. */
    public static String autorDaRequisicao() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof Jwt jwt) {
            CurrentUser usuario = CurrentUserArgumentResolver.JwtClaims.toCurrentUser(jwt);
            return usuario.email();
        }
        return SISTEMA;
    }
}
