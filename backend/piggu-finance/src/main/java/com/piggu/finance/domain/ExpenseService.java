package com.piggu.finance.domain;

import com.piggu.common.error.NotFoundException;
import com.piggu.common.web.Texto;
import com.piggu.finance.api.dto.ExpenseItemRequest;
import com.piggu.finance.api.dto.ExpenseResponse;
import com.piggu.finance.api.dto.SaveExpensesRequest;
import com.piggu.finance.api.dto.UpdateExpenseRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Gastos: o centro do Piggu.
 *
 * <p>Porte de salvarGastos_, atualizarGasto_, excluirGasto_ e validarItens_.</p>
 */
@Service
public class ExpenseService {

    private static final String TIPO_PADRAO = "Variavel";
    private static final String ORIGEM_PADRAO = "Manual";

    private final ExpenseRepository repositorio;
    private final CategoryService categorias;
    private final ProductMemoryService memoriaDeProdutos;

    public ExpenseService(ExpenseRepository repositorio,
                          CategoryService categorias,
                          ProductMemoryService memoriaDeProdutos) {
        this.repositorio = repositorio;
        this.categorias = categorias;
        this.memoriaDeProdutos = memoriaDeProdutos;
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listar() {
        return repositorio.findAllByOrderByExpenseDateDescCreatedAtDesc().stream()
                .map(ExpenseResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listarDoMes(String mes) {
        YearMonth referencia = YearMonth.parse(mes);
        return repositorio.findByExpenseDateBetweenOrderByExpenseDateDesc(
                        referencia.atDay(1), referencia.atEndOfMonth()).stream()
                .map(ExpenseResponse::de)
                .toList();
    }

    /**
     * Grava um lancamento inteiro.
     *
     * <p>Tudo acontece em uma transacao: ou todos os itens do recibo entram, ou nenhum.
     * O Apps Script dependia de um LockService e de uma escrita em bloco para chegar
     * perto disso.</p>
     */
    @Transactional
    public List<ExpenseResponse> salvar(SaveExpensesRequest pedido, String emailUsuario) {
        UUID reciboId = pedido.reciboId() == null ? UUID.randomUUID() : pedido.reciboId();
        String estabelecimento = Texto.limitar(pedido.estabelecimento(), 200);
        String origem = Texto.limitarOuPadrao(pedido.origem(), 30, ORIGEM_PADRAO);

        List<Expense> gastos = pedido.itens().stream()
                .map(item -> montar(item, pedido.data(), reciboId, estabelecimento, origem, emailUsuario))
                .toList();

        List<Expense> salvos = repositorio.saveAll(gastos);
        memoriaDeProdutos.registrar(salvos);

        return salvos.stream().map(ExpenseResponse::de).toList();
    }

    @Transactional
    public ExpenseResponse atualizar(UUID id, UpdateExpenseRequest pedido) {
        Expense gasto = buscar(id);
        gasto.editar(
                Texto.limitar(pedido.item(), 200),
                categorias.normalizar(pedido.categoria()),
                pedido.valor()
        );
        return ExpenseResponse.de(repositorio.save(gasto));
    }

    @Transactional
    public void excluir(UUID id) {
        repositorio.delete(buscar(id));
    }

    /** Remocao silenciosa, usada quando uma nota vinculada e apagada. */
    @Transactional
    public void excluirSeExistir(UUID id) {
        if (id != null) {
            repositorio.findById(id).ifPresent(repositorio::delete);
        }
    }

    private Expense buscar(UUID id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new NotFoundException("Gasto nao encontrado."));
    }

    private Expense montar(ExpenseItemRequest item, LocalDate data, UUID reciboId,
                           String estabelecimento, String origem, String emailUsuario) {
        return new Expense(
                data,
                reciboId,
                estabelecimento,
                Texto.limitar(item.item(), 200),
                categorias.normalizar(item.categoria()),
                item.valor(),
                Texto.limitarOuPadrao(item.tipo(), 30, TIPO_PADRAO),
                origem,
                emailUsuario
        );
    }
}
