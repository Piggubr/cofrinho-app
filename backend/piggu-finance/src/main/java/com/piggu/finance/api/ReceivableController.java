package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.finance.domain.AReceberService;
import com.piggu.finance.domain.Receivable;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Dinheiro que alguem deve para a pessoa; fica dentro de receitas. */
@Tag(name = "A receber", description = "Dinheiro que ainda vai entrar.")
@RestController
@RequestMapping("/api/incomes/receivables")
public class ReceivableController {

    private final AReceberService servico;

    public ReceivableController(AReceberService servico) {
        this.servico = servico;
    }

    public record ReceivableRequest(
            @NotBlank(message = "Diga quem está te devendo.") @Size(max = 80, message = "O nome é longo demais.") String quem,
            @Size(max = 200, message = "A descrição é longa demais.") String descricao,
            @NotNull(message = "Digite o valor.")
            @DecimalMin(value = "0.01", message = "Digite um valor válido.")
            @DecimalMax(value = "10000000", message = "O valor é alto demais.") BigDecimal valor,
            @NotNull(message = "Escolha a data combinada para receber.") LocalDate data) {
    }

    public record ReceivableResponse(UUID id, String quem, String descricao, BigDecimal valor, LocalDate data) {

        static ReceivableResponse de(Receivable conta) {
            return new ReceivableResponse(conta.getId(), conta.getDebtor(), conta.getDescription(), conta.getAmount(),
                    conta.getDueDate());
        }
    }

    /** O que ainda nao foi recebido. */
    @Operation(summary = "Listar o que ainda não foi recebido")
    @GetMapping
    public List<ReceivableResponse> listar() {
        return servico.pendentes().stream().map(ReceivableResponse::de).toList();
    }

    @Operation(summary = "Criar valor a receber")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReceivableResponse criar(@Valid @RequestBody ReceivableRequest pedido, @AuthUser CurrentUser usuario) {
        return ReceivableResponse.de(servico.criar(pedido.quem(), pedido.descricao(), pedido.valor(), pedido.data(),
                usuario.id()));
    }

    /** "Recebi": vira receita de hoje. */
    @Operation(summary = "Marcar como recebido (vira receita de hoje)")
    @PostMapping("/{id}/receive")
    public IncomeController.IncomeResponse receber(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        return IncomeController.IncomeResponse.de(servico.receber(id, usuario.id()));
    }

    @Operation(summary = "Excluir valor a receber")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        servico.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
