package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Meses;
import com.piggu.finance.domain.Income;
import com.piggu.finance.domain.IncomeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Receitas da familia. Como os gastos, so o titular (e o admin) lanca e ve. */
@RestController
@RequestMapping("/api/incomes")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
public class IncomeController {

    private final IncomeService servico;

    public IncomeController(IncomeService servico) {
        this.servico = servico;
    }

    public record IncomeRequest(
            @NotNull(message = "Escolha a data da receita.") LocalDate data,
            @NotBlank(message = "Digite a descricao.") @Size(max = 200, message = "A descricao e longa demais.") String descricao,
            @Size(max = 50) String categoria,
            @NotNull(message = "Digite o valor.")
            @DecimalMin(value = "0.01", message = "Digite um valor valido.")
            @DecimalMax(value = "10000000", message = "O valor e alto demais.") BigDecimal valor) {
    }

    public record IncomeResponse(UUID id, LocalDate data, String descricao, String categoria, BigDecimal valor,
                                 String usuario) {

        static IncomeResponse de(Income receita) {
            return new IncomeResponse(receita.getId(), receita.getIncomeDate(), receita.getDescription(),
                    receita.getCategory(), receita.getAmount(), receita.getUserEmail());
        }
    }

    @GetMapping
    public List<IncomeResponse> listar(@RequestParam(required = false) String mes) {
        return servico.doMes(Meses.ouAtual(mes)).stream().map(IncomeResponse::de).toList();
    }

    @GetMapping("/categories")
    public List<String> categorias() {
        return IncomeService.CATEGORIAS;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncomeResponse criar(@Valid @RequestBody IncomeRequest pedido, @AuthUser CurrentUser usuario) {
        return IncomeResponse.de(servico.criar(pedido.data(), pedido.descricao(), pedido.categoria(), pedido.valor(),
                usuario.email()));
    }

    @PutMapping("/{id}")
    public IncomeResponse atualizar(@PathVariable UUID id, @Valid @RequestBody IncomeRequest pedido) {
        return IncomeResponse.de(servico.atualizar(id, pedido.data(), pedido.descricao(), pedido.categoria(), pedido.valor()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        servico.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
