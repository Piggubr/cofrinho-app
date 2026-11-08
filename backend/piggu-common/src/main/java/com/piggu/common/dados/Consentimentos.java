package com.piggu.common.dados;

import com.piggu.common.error.BusinessException;
import com.piggu.common.security.CurrentUser;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Consentimento gravado no mesmo endpoint que faz o ato (LGPD art. 8).
 *
 * <p>Nada de tela de termos solta: quem manda a foto para a IA ou abre a conexao com
 * o banco e o proprio pedido, e e nele que o consentimento chega, com a versao do
 * aviso que a pessoa viu. Versao diferente da vigente (ou da anterior, na transicao) e recusada: um front antigo
 * nao colhe consentimento sobre um texto que ja saiu do ar, e mudar o aviso invalida
 * os consentimentos antigos sozinho.</p>
 *
 * <p>Fica na tabela {@code consents} de cada servico que faz o ato, com o id da pessoa e a
 * familia, para sair junto na exportacao e na exclusao da conta.</p>
 */
public class Consentimentos {

    /** Versao vigente do aviso de privacidade. Mudou o texto, muda aqui e no front. */
    public static final String VERSAO_DO_AVISO = "2026-11-08";

    /**
     * A versao anterior ainda vale ate {@link #VERSAO_ANTERIOR_VALE_ATE}, para o app ja instalado
     * (que leva a versao no codigo) continuar cadastrando, aceitando e autorizando enquanto a
     * loja entrega a atualizacao (D-180). Na proxima mudanca do aviso, a vigente vira a anterior.
     */
    public static final String VERSAO_ANTERIOR = "2026-10-16";
    public static final LocalDate VERSAO_ANTERIOR_VALE_ATE = LocalDate.of(2027, 1, 7);

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    public static final String CODIGO_NECESSARIO = "CONSENTIMENTO_NECESSARIO";
    public static final String CODIGO_AVISO_MUDOU = "AVISO_DESATUALIZADO";

    private static final Logger log = LoggerFactory.getLogger(Consentimentos.class);

    private final JdbcTemplate jdbc;

    public Consentimentos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A vigente, ou a anterior enquanto durar a transicao. */
    public static boolean versaoAceita(String versao) {
        return versaoAceita(versao, LocalDate.now(BRASILIA));
    }

    static boolean versaoAceita(String versao, LocalDate hoje) {
        return VERSAO_DO_AVISO.equals(versao)
                || (VERSAO_ANTERIOR.equals(versao) && !hoje.isAfter(VERSAO_ANTERIOR_VALE_ATE));
    }

    public boolean jaAutorizou(CurrentUser usuario, String finalidade) {
        String anterior = versaoAceita(VERSAO_ANTERIOR) ? VERSAO_ANTERIOR : VERSAO_DO_AVISO;
        Integer vezes = jdbc.queryForObject(
                "SELECT count(*) FROM consents WHERE household_id = ? AND user_id = ? AND purpose = ?"
                        + " AND notice_version IN (?, ?) AND revoked_at IS NULL",
                Integer.class, usuario.familia(), usuario.id(), finalidade, VERSAO_DO_AVISO, anterior);
        return vezes != null && vezes > 0;
    }

    /**
     * Para o que basta autorizar uma vez por versao do aviso (ler foto com IA): segue se
     * a pessoa ja autorizou, ou se autoriza agora (e entao grava). Sem isso, recusa com
     * o codigo que faz o front mostrar o aviso.
     *
     * @param autorizo      o que veio no pedido; nulo quando o front nao perguntou
     * @param versaoDoAviso versao do texto que a pessoa viu
     */
    public void exigir(CurrentUser usuario, String finalidade, String descricao, Boolean autorizo, String versaoDoAviso) {
        if (Boolean.TRUE.equals(autorizo)) {
            gravar(usuario, finalidade, versaoDoAviso);
        } else if (!jaAutorizou(usuario, finalidade)) {
            throw new BusinessException(descricao, HttpStatus.UNPROCESSABLE_CONTENT, CODIGO_NECESSARIO);
        }
    }

    /** Para o que pede autorizacao a cada vez (cada banco conectado e um compartilhamento novo). */
    public void exigirAgora(CurrentUser usuario, String finalidade, String descricao, Boolean autorizo, String versaoDoAviso) {
        if (!Boolean.TRUE.equals(autorizo)) {
            throw new BusinessException(descricao, HttpStatus.UNPROCESSABLE_CONTENT, CODIGO_NECESSARIO);
        }
        gravar(usuario, finalidade, versaoDoAviso);
    }

    /**
     * Retira o consentimento (LGPD art. 8, par. 5): marca a data e, dali em diante, o
     * servico volta a pedir antes de enviar. A linha fica como prova do que valeu ate ali.
     *
     * @return quantas autorizacoes estavam valendo
     */
    public int retirar(CurrentUser usuario, String finalidade) {
        int retiradas = jdbc.update("UPDATE consents SET revoked_at = now() WHERE household_id = ? AND user_id = ?"
                + " AND purpose = ? AND revoked_at IS NULL", usuario.familia(), usuario.id(), finalidade);
        log.info("Consentimento retirado: conta={} finalidade={} linhas={}", usuario.id(), finalidade, retiradas);
        return retiradas;
    }

    private void gravar(CurrentUser usuario, String finalidade, String versaoDoAviso) {
        if (!versaoAceita(versaoDoAviso)) {
            throw new BusinessException("O aviso de privacidade mudou. Recarregue a pagina e leia de novo.",
                    HttpStatus.CONFLICT, CODIGO_AVISO_MUDOU);
        }
        jdbc.update("INSERT INTO consents (household_id, user_id, purpose, notice_version) VALUES (?, ?, ?, ?)",
                usuario.familia(), usuario.id(), finalidade, versaoDoAviso);
        log.info("Consentimento registrado: conta={} finalidade={} versao={}", usuario.id(), finalidade, versaoDoAviso);
    }
}
