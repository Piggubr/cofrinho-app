package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Meses;
import com.piggu.finance.api.dto.ExpenseResponse;
import com.piggu.finance.domain.ContasFixasService;
import com.piggu.finance.domain.RecurringBill;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

/** Contas fixas recorrentes da familia. */
@RestController
@RequestMapping("/api/bills")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
public class BillController {

    private final ContasFixasService servico;

    public BillController(ContasFixasService servico) {
        this.servico = servico;
    }

    public record BillRequest(
            @NotBlank(message = "Digite a descricao.") @Size(max = 200, message = "A descricao e longa demais.") String descricao,
            @Size(max = 50) String categoria,
            @NotNull(message = "Digite o valor.")
            @DecimalMin(value = "0.01", message = "Digite um valor valido.")
            @DecimalMax(value = "1000000", message = "O valor e alto demais.") BigDecimal valor,
            @NotNull(message = "Escolha o dia do vencimento.")
            @Min(value = 1, message = "O dia vai de 1 a 31.") @Max(value = 31, message = "O dia vai de 1 a 31.") Integer dia,
            Boolean automatico) {
    }

    public record BillResponse(UUID id, String descricao, String categoria, BigDecimal valor, int dia,
                               boolean automatico, LocalDate vencimento, ContasFixasService.Situacao situacao) {

        static BillResponse de(ContasFixasService.ContaDoMes item) {
            RecurringBill conta = item.conta();
            return new BillResponse(conta.getId(), conta.getDescription(), conta.getCategory(), conta.getAmount(),
                    conta.getDueDay(), conta.isAutoLaunch(), item.vencimento(), item.situacao());
        }
    }

    /** As contas no mes pedido (ou no atual), com vencimento e situacao. */
    @GetMapping
    public List<BillResponse> listar(@RequestParam(required = false) String mes) {
        return servico.doMes(Meses.ouAtual(mes)).stream().map(BillResponse::de).toList();
    }

    @PostMapping
    public ResponseEntity<Void> criar(@Valid @RequestBody BillRequest pedido, @AuthUser CurrentUser usuario) {
        servico.criar(pedido.descricao(), pedido.categoria(), pedido.valor(), pedido.dia(),
                Boolean.TRUE.equals(pedido.automatico()), usuario.email());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> atualizar(@PathVariable UUID id, @Valid @RequestBody BillRequest pedido) {
        servico.atualizar(id, pedido.descricao(), pedido.categoria(), pedido.valor(), pedido.dia(),
                Boolean.TRUE.equals(pedido.automatico()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        servico.excluir(id);
        return ResponseEntity.noContent().build();
    }

    /** Marca como paga: lanca o gasto do mes. Lancar duas vezes o mesmo mes e 409. */
    @PostMapping("/{id}/pay")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse pagar(@PathVariable UUID id, @RequestParam(required = false) String mes,
                                 @AuthUser CurrentUser usuario) {
        return servico.pagar(id, Meses.ouAtual(mes), usuario.email());
    }
}
