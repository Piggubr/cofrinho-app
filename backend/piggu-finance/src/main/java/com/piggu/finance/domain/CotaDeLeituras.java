package com.piggu.finance.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.Plano;
import com.piggu.common.web.Meses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Quantas notas a familia leu pela foto no mes.
 *
 * <p>No gratuito a leitura propria vale ate {@code gratisPorMes} notas; acima disso,
 * e a reserva pela IA, e Premium. O mes vira no horario de Brasilia. SQL direto (sem o
 * filtro por familia): toda instrucao leva household_id.</p>
 */
@Component
public class CotaDeLeituras {

    private final JdbcTemplate jdbc;
    private final int gratisPorMes;
    private final Clock relogio;

    @Autowired
    public CotaDeLeituras(JdbcTemplate jdbc, @Value("${piggu.leitura-de-nota.gratis-por-mes:10}") int gratisPorMes) {
        this(jdbc, gratisPorMes, Clock.system(Meses.BRASILIA));
    }

    CotaDeLeituras(JdbcTemplate jdbc, int gratisPorMes, Clock relogio) {
        this.jdbc = jdbc;
        this.gratisPorMes = gratisPorMes;
        this.relogio = relogio;
    }

    /** @param usadas leituras ja feitas no mes; {@code restantes} nulo = sem limite (Premium) */
    public record Uso(int usadas, int limite, Integer restantes) {
    }

    public Uso uso(CurrentUser usuario) {
        int usadas = usadasNoMes(usuario.familia());
        return usuario.isPremium()
                ? new Uso(usadas, gratisPorMes, null)
                : new Uso(usadas, gratisPorMes, Math.max(0, gratisPorMes - usadas));
    }

    /**
     * Conta a leitura antes de ler, numa instrucao so que no gratuito so soma abaixo do limite:
     * conferir e somar separados deixava leituras ao mesmo tempo passarem todas do limite. Em
     * transacao propria, para a reserva valer na hora e nao prender a linha durante a leitura.
     * Se a leitura falhar, {@link #devolver}.
     *
     * @return quantas sobram no mes (nulo no Premium)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Integer reservar(CurrentUser usuario) {
        int limite = usuario.isPremium() ? Integer.MAX_VALUE : gratisPorMes;
        Integer usadas = limite <= 0 ? null : jdbc.query("""
                INSERT INTO receipt_usage (household_id, reference_month, reads) VALUES (?, ?, 1)
                ON CONFLICT (household_id, reference_month) DO UPDATE SET reads = receipt_usage.reads + 1
                    WHERE receipt_usage.reads < ?
                RETURNING reads
                """, linha -> linha.next() ? linha.getInt(1) : null, usuario.familia(), mes(), limite);
        if (usadas == null) {
            throw new BusinessException("Sua família já leu as " + gratisPorMes + " notas grátis deste mês. "
                    + "Com o Premium a leitura pela foto não tem limite; ou lance os itens à mão.",
                    HttpStatus.UNPROCESSABLE_CONTENT, Plano.CODIGO_PREMIUM);
        }
        return usuario.isPremium() ? null : Math.max(0, gratisPorMes - usadas);
    }

    /** A leitura reservada falhou: volta para a cota. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void devolver(CurrentUser usuario) {
        jdbc.update("UPDATE receipt_usage SET reads = reads - 1 WHERE household_id = ? AND reference_month = ? AND reads > 0",
                usuario.familia(), mes());
    }

    private int usadasNoMes(UUID familia) {
        Integer usadas = jdbc.query("SELECT reads FROM receipt_usage WHERE household_id = ? AND reference_month = ?",
                linha -> linha.next() ? linha.getInt(1) : 0, familia, mes());
        return usadas == null ? 0 : usadas;
    }

    private String mes() {
        return YearMonth.now(relogio).toString();
    }
}
