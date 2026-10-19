package com.piggu.finance.domain;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.web.Meses;
import com.piggu.common.web.Texto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dinheiro que alguem deve para a pessoa. Nao conta como receita ate ela tocar em
 * "Recebi": ai vira uma receita com a data do dia, no mes em que o dinheiro entrou.
 */
@Service
public class AReceberService {

    private final ReceivableRepository repositorio;
    private final IncomeService receitas;
    private final TrilhaDeAuditoria trilha;

    public AReceberService(ReceivableRepository repositorio, IncomeService receitas, TrilhaDeAuditoria trilha) {
        this.repositorio = repositorio;
        this.receitas = receitas;
        this.trilha = trilha;
    }

    /** O que ainda nao foi recebido, do mais antigo para o mais novo. */
    @Transactional(readOnly = true)
    public List<Receivable> pendentes() {
        return repositorio.findByReceivedAtIsNullOrderByDueDateAsc();
    }

    @Transactional
    public Receivable criar(String quem, String descricao, BigDecimal valor, LocalDate combinado, UUID autor) {
        String devedor = Texto.limitar(Texto.espacoUnico(quem), 80);
        if (devedor.isBlank()) {
            throw new BusinessException("Diga quem está te devendo.");
        }
        Receivable conta = repositorio.save(new Receivable(devedor, Texto.limitar(Texto.espacoUnico(descricao), 200),
                valor, combinado, autor));
        trilha.criou("a-receber", conta.getId(), Resumos.aReceber(conta));
        return conta;
    }

    /** Lanca a receita hoje e tira da lista; tocar duas vezes nao lanca duas receitas. */
    @Transactional
    public Income receber(UUID id, UUID autor) {
        Receivable conta = buscar(id);
        if (conta.isRecebida()) {
            throw new BusinessException("Esse valor já foi recebido.", HttpStatus.CONFLICT, "JA_RECEBIDO");
        }
        LocalDate hoje = LocalDate.now(Meses.BRASILIA);
        String descricao = conta.getDescription().isBlank() ? conta.getDebtor()
                : conta.getDebtor() + " · " + conta.getDescription();
        Income receita = receitas.criar(hoje, descricao, "Reembolso", conta.getAmount(), autor);
        conta.recebida(hoje, receita.getId());
        return receita;
    }

    @Transactional
    public void excluir(UUID id) {
        Receivable conta = buscar(id);
        repositorio.delete(conta);
        trilha.apagou("a-receber", id, Resumos.aReceber(conta));
    }

    private Receivable buscar(UUID id) {
        return repositorio.findById(id).orElseThrow(() -> new NotFoundException("Valor a receber não encontrado."));
    }
}
