package com.piggu.common.dados;

import com.piggu.common.error.BusinessException;
import com.piggu.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Consentimento gravado no mesmo endpoint que faz o ato (LGPD art. 8).
 *
 * <p>Nada de tela de termos solta: quem manda a foto para a IA ou abre a conexao com
 * o banco e o proprio pedido, e e nele que o consentimento chega, com a versao do
 * aviso que a pessoa viu. Versao diferente da vigente e recusada: um front antigo
 * nao colhe consentimento sobre um texto que ja saiu do ar, e mudar o aviso invalida
 * os consentimentos antigos sozinho.</p>
 *
 * <p>Fica na tabela {@code consents} de cada servico que faz o ato, com e-mail e
 * familia, para sair junto na exportacao e na exclusao da conta.</p>
 */
public class Consentimentos {

    /** Versao vigente do aviso de privacidade. Mudou o texto, muda aqui e no front. */
    public static final String VERSAO_DO_AVISO = "2026-10-08";

    public static final String CODIGO_NECESSARIO = "CONSENTIMENTO_NECESSARIO";
    public static final String CODIGO_AVISO_MUDOU = "AVISO_DESATUALIZADO";

    private static final Logger log = LoggerFactory.getLogger(Consentimentos.class);

    private final JdbcTemplate jdbc;

    public Consentimentos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean jaAutorizou(CurrentUser usuario, String finalidade) {
        Integer vezes = jdbc.queryForObject(
                "SELECT count(*) FROM consents WHERE household_id = ? AND user_email = ? AND purpose = ? AND notice_version = ?",
                Integer.class, usuario.familia(), usuario.email(), finalidade, VERSAO_DO_AVISO);
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

    private void gravar(CurrentUser usuario, String finalidade, String versaoDoAviso) {
        if (!VERSAO_DO_AVISO.equals(versaoDoAviso)) {
            throw new BusinessException("O aviso de privacidade mudou. Recarregue a pagina e leia de novo.",
                    HttpStatus.CONFLICT, CODIGO_AVISO_MUDOU);
        }
        jdbc.update("INSERT INTO consents (household_id, user_email, purpose, notice_version) VALUES (?, ?, ?, ?)",
                usuario.familia(), usuario.email(), finalidade, VERSAO_DO_AVISO);
        log.info("Consentimento registrado: conta={} finalidade={} versao={}", usuario.id(), finalidade, VERSAO_DO_AVISO);
    }
}
