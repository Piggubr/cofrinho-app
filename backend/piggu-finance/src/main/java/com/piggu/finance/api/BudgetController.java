package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Meses;
import com.piggu.finance.domain.OrcamentoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Orcamento por categoria. Criar e mudar limite e Premium; ver e apagar, nao. */
@RestController
@RequestMapping("/api/budgets")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
public class BudgetController {

    private final OrcamentoService servico;

    public BudgetController(OrcamentoService servico) {
        this.servico = servico;
    }

    public record BudgetRequest(
            @NotBlank(message = "Escolha a categoria.") @Size(max = 50) String categoria,
            @NotNull(message = "Digite o limite.")
            @DecimalMin(value = "0.01", message = "Digite um limite valido.")
            @DecimalMax(value = "1000000", message = "O limite e alto demais.") BigDecimal limite) {
    }

    /** Cada categoria com orcamento no mes: limite, gasto, percentual e alerta (80% e 100%). */
    @GetMapping
    public List<OrcamentoService.Situacao> listar(@RequestParam(required = false) String mes) {
        return servico.doMes(Meses.ouAtual(mes));
    }

    @PutMapping
    public ResponseEntity<Void> definir(@Valid @RequestBody BudgetRequest pedido, @AuthUser CurrentUser usuario) {
        servico.definir(pedido.categoria(), pedido.limite(), usuario);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        servico.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
