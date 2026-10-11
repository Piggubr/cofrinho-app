package com.piggu.common.dados;

import com.piggu.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Exporta e apaga os dados de uma pessoa (LGPD art. 18, II, V e VI) num servico.
 *
 * <p>Cada servico declara as tabelas em que guarda dado de alguem e a coluna que diz
 * quem lancou. Isso basta para tres operacoes:</p>
 * <ul>
 *   <li><b>exportar</b>: o titular leva a familia inteira; o membro, o que ele lancou;</li>
 *   <li><b>apagar a familia</b>: quando a pessoa era a ultima da familia, tudo sai;</li>
 *   <li><b>apagar a pessoa</b>: a familia continua, entao o que era compartilhado fica,
 *       anonimizado, e o que era so dela ({@link Tabela#pessoal()}) sai.</li>
 * </ul>
 *
 * <p>SQL direto, entao o filtro automatico por familia nao vale aqui: toda instrucao
 * leva {@code household_id = ?} escrito. A lista de tabelas e a definicao de "tudo":
 * tabela nova com dado pessoal que nao entrar nela deixa a exclusao incompleta sem
 * nenhum aviso.</p>
 */
public class DadosDaFamilia {

    private static final Pattern NOME_SQL = Pattern.compile("^[a-z_]+$");
    private static final Logger log = LoggerFactory.getLogger(DadosDaFamilia.class);

    /**
     * @param nome    tabela com a coluna household_id
     * @param autor   coluna com o id de quem lancou; nula quando a tabela nao tem autor. Na
     *                exclusao da pessoa ela vira nula: o lancamento fica com a familia, sem dono
     * @param pessoal verdadeiro quando o registro e so da pessoa e sai mesmo que a familia fique
     */
    public record Tabela(String nome, String autor, boolean pessoal) {

        public Tabela {
            if (!NOME_SQL.matcher(nome).matches() || (autor != null && !NOME_SQL.matcher(autor).matches())) {
                throw new IllegalArgumentException("Nome de tabela ou coluna invalido: " + nome);
            }
        }

        public static Tabela compartilhada(String nome, String autor) {
            return new Tabela(nome, autor, false);
        }

        public static Tabela pessoal(String nome, String autor) {
            return new Tabela(nome, autor, true);
        }
    }

    /** O que o servico precisa fazer alem do SQL (arquivos, provedores externos). */
    public interface AoApagar {
        void antes(UUID familia, UUID pessoa, EscopoDeExclusao escopo);
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final List<Tabela> tabelas;
    private final List<AoApagar> extras;

    /** @param tabelas em ordem de exclusao: quem aponta para outra tabela vem antes dela */
    public DadosDaFamilia(JdbcTemplate jdbc, ObjectMapper json, List<Tabela> tabelas, List<AoApagar> extras) {
        this.jdbc = jdbc;
        this.json = json;
        this.tabelas = List.copyOf(tabelas);
        this.extras = List.copyOf(extras);
    }

    @Transactional(readOnly = true)
    public Map<String, JsonNode> exportar(CurrentUser usuario) {
        Map<String, JsonNode> dados = new LinkedHashMap<>();
        for (Tabela tabela : tabelas) {
            if (!usuario.isTitular() && tabela.autor() == null) {
                continue;
            }
            String sql = "SELECT coalesce(jsonb_agg(t), '[]'::jsonb)::text FROM " + tabela.nome() + " t WHERE household_id = ?";
            String linhas = usuario.isTitular()
                    ? jdbc.queryForObject(sql, String.class, usuario.familia())
                    : jdbc.queryForObject(sql + " AND " + tabela.autor() + " = ?", String.class,
                            usuario.familia(), usuario.id());
            dados.put(tabela.nome(), ler(linhas));
        }
        return dados;
    }

    /** Tudo numa transacao: meio apagado e o unico resultado inaceitavel. */
    @Transactional
    public void apagar(CurrentUser usuario, EscopoDeExclusao escopo) {
        UUID familia = usuario.familia();
        extras.forEach(extra -> extra.antes(familia, usuario.id(), escopo));
        int linhas = 0;
        for (Tabela tabela : tabelas) {
            if (escopo == EscopoDeExclusao.FAMILIA) {
                linhas += jdbc.update("DELETE FROM " + tabela.nome() + " WHERE household_id = ?", familia);
            } else if (tabela.autor() != null && tabela.pessoal()) {
                linhas += jdbc.update("DELETE FROM " + tabela.nome() + " WHERE household_id = ? AND "
                        + tabela.autor() + " = ?", familia, usuario.id());
            } else if (tabela.autor() != null) {
                linhas += jdbc.update("UPDATE " + tabela.nome() + " SET " + tabela.autor() + " = NULL WHERE household_id = ? AND "
                        + tabela.autor() + " = ?", familia, usuario.id());
            }
        }
        log.info("Dados apagados: conta={} familia={} escopo={} linhas={}", usuario.id(), familia, escopo, linhas);
    }

    private JsonNode ler(String linhas) {
        try {
            return json.readTree(linhas);
        } catch (Exception erro) {
            throw new IllegalStateException("Exportacao gerou JSON invalido", erro);
        }
    }
}
