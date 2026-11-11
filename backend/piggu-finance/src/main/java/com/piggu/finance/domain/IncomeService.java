package com.piggu.finance.domain;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.FamiliaAtual;
import com.piggu.common.web.Texto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Receitas da familia. */
@Service
public class IncomeService {

    /** Categorias de receita; texto livre cai em Outros, como nos gastos. */
    public static final List<String> CATEGORIAS = List.of("Salário", "Extra", "Reembolso", "Investimentos", "Outros");

    private static final Logger log = LoggerFactory.getLogger(IncomeService.class);

    private final IncomeRepository repositorio;
    private final TrilhaDeAuditoria trilha;
    private final JdbcTemplate jdbc;

    public IncomeService(IncomeRepository repositorio, TrilhaDeAuditoria trilha, JdbcTemplate jdbc) {
        this.repositorio = repositorio;
        this.trilha = trilha;
        this.jdbc = jdbc;
    }

    /**
     * Receita com o id do envio do app, como o gasto ({@code ExpenseService.umaVezSo}): o mesmo
     * envio de novo nao lanca nada e devolve a receita da primeira vez (D-192). O id entra na
     * transacao da receita; dois envios ao mesmo tempo esperam um pelo outro na chave.
     */
    @Transactional
    public Income umaVezSo(UUID idDoEnvio, UUID usuarioId, Supplier<Income> lancar) {
        if (idDoEnvio == null) {
            return lancar.get();
        }
        UUID familia = FamiliaAtual.atual();
        if (jdbc.update("INSERT INTO income_submissions (household_id, id, user_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                familia, idDoEnvio, usuarioId) == 0) {
            UUID id = jdbc.queryForObject("SELECT income_id FROM income_submissions WHERE household_id = ? AND id = ?",
                    UUID.class, familia, idDoEnvio);
            log.info("Envio repetido de receita: id={} receita={}", idDoEnvio, id);
            return buscar(id);
        }
        Income receita = lancar.get();
        jdbc.update("UPDATE income_submissions SET income_id = ? WHERE household_id = ? AND id = ?",
                receita.getId(), familia, idDoEnvio);
        return receita;
    }

    @Transactional(readOnly = true)
    public List<Income> doMes(YearMonth mes) {
        return repositorio.findByIncomeDateBetweenOrderByIncomeDateDescCreatedAtDesc(mes.atDay(1), mes.atEndOfMonth());
    }

    @Transactional
    public Income criar(LocalDate data, String descricao, String categoria, BigDecimal valor, UUID usuarioId) {
        return criar(data, descricao, categoria, valor, usuarioId, false);
    }

    /** @param compartilhado aparece para todos da familia, nao so para quem lancou */
    @Transactional
    public Income criar(LocalDate data, String descricao, String categoria, BigDecimal valor, UUID usuarioId,
                        boolean compartilhado) {
        Income nova = new Income(data, Texto.limitar(descricao, 200), categoria(categoria), valor, usuarioId);
        nova.compartilhar(compartilhado);
        Income receita = repositorio.save(nova);
        trilha.criou("receita", receita.getId(), Resumos.receita(receita));
        log.info("Receita lancada: id={}", receita.getId());
        return receita;
    }

    /**
     * Compartilhada, os dois editam; so quem lancou muda o compartilhamento.
     *
     * @param compartilhado nulo mantem como esta
     */
    @Transactional
    public Income atualizar(UUID id, LocalDate data, String descricao, String categoria, BigDecimal valor,
                            Boolean compartilhado, CurrentUser usuario) {
        Income receita = buscar(id);
        Compartilhamento.exigirEdicao(usuario, receita.getUserId(), receita.isShared(), "a receita");
        if (Compartilhamento.muda(usuario, receita.getUserId(), receita.isShared(), compartilhado, "a receita")) {
            receita.compartilhar(compartilhado);
        }
        String antes = Resumos.receita(receita);
        receita.editar(data, Texto.limitar(descricao, 200), categoria(categoria), valor);
        trilha.editou("receita", id, antes, Resumos.receita(receita));
        return receita;
    }

    @Transactional
    public void excluir(UUID id, CurrentUser usuario) {
        Income receita = buscar(id);
        Compartilhamento.exigirDono(usuario, receita.getUserId(), "a receita");
        repositorio.delete(receita);
        trilha.apagou("receita", id, Resumos.receita(receita));
        log.info("Receita apagada: id={}", id);
    }

    private Income buscar(UUID id) {
        return repositorio.findById(id).orElseThrow(() -> new NotFoundException("Receita nao encontrada."));
    }

    static String categoria(String informada) {
        return CATEGORIAS.stream().filter(c -> c.equalsIgnoreCase(Texto.limitar(informada, 50).trim()))
                .findFirst().orElse("Outros");
    }
}
