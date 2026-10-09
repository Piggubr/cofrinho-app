package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Meses;
import com.piggu.finance.api.dto.ExpenseResponse;
import com.piggu.finance.api.dto.SaveExpensesRequest;
import com.piggu.finance.api.dto.UpdateExpenseRequest;
import com.piggu.finance.domain.DivisaoDeGastos;
import com.piggu.finance.domain.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Gastos.
 *
 * <p>Substitui as acoes save, updateExpense e deleteExpense. O perfil MEMBRO nao
 * alcanca nada aqui, como era em autorizarAcao_.</p>
 */
@RestController
@RequestMapping("/api/expenses")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
public class ExpenseController {

    private final ExpenseService servico;
    private final DivisaoDeGastos divisao;

    public ExpenseController(ExpenseService servico, DivisaoDeGastos divisao) {
        this.servico = servico;
        this.divisao = divisao;
    }

    /** Acerto dos gastos divididos no mes: quanto cada pessoa pagou, a parte dela e o saldo. */
    @GetMapping("/splits")
    public List<DivisaoDeGastos.Acerto> acerto(@RequestParam(required = false) String mes) {
        return divisao.acerto(Meses.ouAtual(mes));
    }

    /**
     * @param mes filtro opcional no formato AAAA-MM; ausente devolve tudo
     */
    @GetMapping
    public List<ExpenseResponse> listar(@RequestParam(required = false) String mes) {
        return mes == null || mes.isBlank() ? servico.listar() : servico.listarDoMes(mes);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<ExpenseResponse> salvar(@Valid @RequestBody SaveExpensesRequest pedido,
                                        @AuthUser CurrentUser usuario) {
        return servico.salvar(pedido, usuario.email());
    }

    @PutMapping("/{id}")
    public ExpenseResponse atualizar(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateExpenseRequest pedido) {
        return servico.atualizar(id, pedido);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        servico.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
