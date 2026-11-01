package com.piggu.common.auditoria;

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

    /**
     * Eventos do que so a pessoa ve (orcamento e meta do mes, como no {@code DonoAtual.SO_DA_PESSOA}; os dados da
     * conta no identity): ninguem mais da familia ve, nem o titular (D-154).
     */
    public static final java.util.Set<String> ENTIDADES_PRIVADAS = java.util.Set.of("orcamento", "meta", "conta");

    /** Autor de mudanca feita por job agendado (conta fixa automatica, resgate). */
    public static final UUID SISTEMA = new UUID(0L, 0L);

    private static final int LIMITE_DO_RESUMO = 300;

    /**
     * Autor escolhido por um job para o que ele faz em nome de alguem (ver {@link #emNomeDe}).
     * Sem valor: nenhum job escolheu; {@code EmNomeDe(null)}: o registro ficou sem dono.
     */
    private static final ThreadLocal<EmNomeDe> EM_NOME_DE = new ThreadLocal<>();

    private record EmNomeDe(UUID autor) {
    }
    private static final Logger log = LoggerFactory.getLogger(TrilhaDeAuditoria.class);

    public enum Acao { CRIOU, EDITOU, APAGOU, IMPORTOU, PAGOU, MUDOU_PAPEL, REMOVEU, ENTROU, SAIU, CONVIDOU }

    /**
     * @param autor id de quem fez; {@link #SISTEMA} quando foi um job agendado; nulo quando
     *              a conta de quem fez foi excluida depois
     */
    public record Evento(long id, UUID autor, Acao acao, String entidade, String entidadeId,
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
    public void registrar(UUID familia, UUID autor, Acao acao, String entidade, Object id, String antes, String depois) {
        jdbc.update("""
                INSERT INTO eventos_de_auditoria
                    (household_id, autor_id, acao, entidade, entidade_id, antes, depois, criado_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                familia, autor, acao.name(), entidade, id == null ? null : id.toString(),
                curto(antes), curto(depois),
                Timestamp.from(relogio.instant()));
    }

    /** Os mais recentes da familia, de todos os autores (exportacao da familia). */
    public List<Evento> recentes(UUID familia, String entidade, int limite) {
        return recentes(familia, entidade, limite, null, true);
    }

    /**
     * Os mais recentes da familia, para a tela de historico. SQL puro: filtra a familia (e os
     * autores) a mao. Evento de {@link #ENTIDADES_PRIVADAS} so aparece para quem o fez.
     *
     * @param autores  so o que estas pessoas (e o sistema) fizeram; nulo = todos. A primeira e
     *                 quem pede ({@code CurrentUser.visiveis()})
     * @param semAutor inclui o que ficou sem autor (a familia, sem a pessoa que saiu)
     */
    public List<Evento> recentes(UUID familia, String entidade, int limite, List<UUID> autores, boolean semAutor) {
        List<Object> parametros = new java.util.ArrayList<>(List.of(familia));
        String sql = """
                SELECT id, autor_id, acao, entidade, entidade_id, antes, depois, criado_em
                FROM eventos_de_auditoria WHERE household_id = ?
                """;
        if (entidade != null) {
            sql += " AND entidade = ?";
            parametros.add(entidade);
        }
        if (autores != null) {
            sql += " AND (autor_id = ANY(?) OR (autor_id IS NULL AND ?))";
            List<UUID> comSistema = new java.util.ArrayList<>(autores);
            comSistema.add(SISTEMA);
            parametros.add(comSistema.toArray(UUID[]::new));
            parametros.add(semAutor);
            sql += " AND (entidade <> ALL(?) OR autor_id = ?)";
            parametros.add(ENTIDADES_PRIVADAS.toArray(String[]::new));
            parametros.add(autores.getFirst());
        }
        sql += " ORDER BY id DESC LIMIT ?";
        parametros.add(limite);
        return jdbc.query(sql, (linha, n) -> new Evento(
                linha.getLong("id"),
                linha.getObject("autor_id", UUID.class),
                Acao.valueOf(linha.getString("acao")),
                linha.getString("entidade"),
                linha.getString("entidade_id"),
                linha.getString("antes"),
                linha.getString("depois"),
                linha.getTimestamp("criado_em").toInstant()), parametros.toArray());

    }

    /**
     * Para o servico que nao usa o {@code DadosDaFamilia} (o identity): a familia sumiu,
     * os eventos dela saem; a pessoa saiu, o que e so dela ({@link #ENTIDADES_PRIVADAS}) sai
     * junto e o resto (entrou, mudou de papel) fica com a familia, sem autor (D-154).
     */
    public void esquecer(UUID familia, UUID pessoa, boolean familiaInteira) {
        if (familiaInteira) {
            jdbc.update("DELETE FROM eventos_de_auditoria WHERE household_id = ?", familia);
        } else {
            jdbc.update("DELETE FROM eventos_de_auditoria WHERE household_id = ? AND autor_id = ? AND entidade = ANY(?)",
                    familia, pessoa, ENTIDADES_PRIVADAS.toArray(String[]::new));
            jdbc.update("UPDATE eventos_de_auditoria SET autor_id = NULL WHERE household_id = ? AND autor_id = ?",
                    familia, pessoa);
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

    /**
     * Roda {@code tarefa} de um job registrando os eventos em nome de {@code autor}: o que o job
     * faz pelo registro de alguem (conta fixa automatica) aparece no historico so para quem ve os
     * dados dessa pessoa. Evento do {@link #SISTEMA} aparece para a familia inteira.
     *
     * @param autor dono do registro; nulo quando ficou sem dono (vai para titular e parceiro)
     */
    public static void emNomeDe(UUID autor, Runnable tarefa) {
        EmNomeDe anterior = EM_NOME_DE.get();
        EM_NOME_DE.set(new EmNomeDe(autor));
        try {
            tarefa.run();
        } finally {
            if (anterior == null) {
                EM_NOME_DE.remove();
            } else {
                EM_NOME_DE.set(anterior);
            }
        }
    }

    /**
     * Id de quem esta na requisicao; sem requisicao (job agendado), quem o job escolheu em
     * {@link #emNomeDe} ou o {@link #SISTEMA}.
     */
    public static UUID autorDaRequisicao() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof Jwt jwt) {
            CurrentUser usuario = CurrentUserArgumentResolver.JwtClaims.toCurrentUser(jwt);
            return usuario.id();
        }
        EmNomeDe emNomeDe = EM_NOME_DE.get();
        return emNomeDe != null ? emNomeDe.autor() : SISTEMA;
    }
}
