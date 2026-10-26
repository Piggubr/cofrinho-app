package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Meses;
import com.piggu.finance.domain.ContasFixasService;
import com.piggu.finance.domain.Income;
import com.piggu.finance.domain.IncomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/** Receitas. Como os gastos, cada um ve as suas e as que os outros compartilharam. */
@Tag(name = "Receitas", description = "Dinheiro que entrou.")
@RestController
@RequestMapping("/api/incomes")
public class IncomeController {

    private final IncomeService servico;
    private final ContasFixasService recorrentes;

    public IncomeController(IncomeService servico, ContasFixasService recorrentes) {
        this.servico = servico;
        this.recorrentes = recorrentes;
    }

    public record IncomeRequest(
            @NotNull(message = "Escolha a data da receita.") LocalDate data,
            @NotBlank(message = "Digite a descricao.") @Size(max = 200, message = "A descricao e longa demais.") String descricao,
            @Size(max = 50) String categoria,
            @NotNull(message = "Digite o valor.")
            @DecimalMin(value = "0.01", message = "Digite um valor valido.")
            @DecimalMax(value = "10000000", message = "O valor e alto demais.") BigDecimal valor,
            Boolean recorrente,
            Boolean compartilhado) {
    }

    public record IncomeResponse(UUID id, LocalDate data, String descricao, String categoria, BigDecimal valor,
                                 UUID usuario, boolean compartilhado) {

        static IncomeResponse de(Income receita) {
            return new IncomeResponse(receita.getId(), receita.getIncomeDate(), receita.getDescription(),
                    receita.getCategory(), receita.getAmount(), receita.getUserId(), receita.isShared());
        }
    }

    @Operation(summary = "Listar receitas")
    @GetMapping
    public List<IncomeResponse> listar(@RequestParam(required = false) String mes) {
        return servico.doMes(Meses.ouAtual(mes)).stream().map(IncomeResponse::de).toList();
    }

    @Operation(summary = "Categorias de receita")
    @GetMapping("/categories")
    public List<String> categorias() {
        return IncomeService.CATEGORIAS;
    }

    @Operation(summary = "Lançar receita")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncomeResponse criar(@Valid @RequestBody IncomeRequest pedido, @AuthUser CurrentUser usuario) {
        boolean compartilhado = Boolean.TRUE.equals(pedido.compartilhado());
        if (Boolean.TRUE.equals(pedido.recorrente())) {
            return IncomeResponse.de(recorrentes.lancarReceitaRecorrente(pedido.data(), pedido.descricao(),
                    pedido.categoria(), pedido.valor(), usuario.id(), compartilhado));
        }
        return IncomeResponse.de(servico.criar(pedido.data(), pedido.descricao(), pedido.categoria(), pedido.valor(),
                usuario.id(), compartilhado));
    }

    @Operation(summary = "Alterar receita")
    @PutMapping("/{id}")
    public IncomeResponse atualizar(@PathVariable UUID id, @Valid @RequestBody IncomeRequest pedido,
                                    @AuthUser CurrentUser usuario) {
        return IncomeResponse.de(servico.atualizar(id, pedido.data(), pedido.descricao(), pedido.categoria(),
                pedido.valor(), pedido.compartilhado(), usuario));
    }

    @Operation(summary = "Excluir receita")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        servico.excluir(id, usuario);
        return ResponseEntity.noContent().build();
    }
}
