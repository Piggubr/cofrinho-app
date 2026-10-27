package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Meses;
import com.piggu.finance.api.dto.ExpenseResponse;
import com.piggu.finance.domain.ContasFixasService;
import com.piggu.finance.domain.RecurringBill;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Contas fixas", description = "Contas que vencem todo mês e receitas recorrentes.")
@RestController
@RequestMapping("/api/bills")
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
            Boolean automatico,
            Boolean compartilhado) {
    }

    /**
     * @param contaId    cartao ou conta que paga o gasto; nulo quando nao foi informado
     * @param segundoDia segunda parcela da receita (o salario do dia 20 e do dia 5); nulo quando entra de uma vez
     */
    public record BillResponse(UUID id, String descricao, String categoria, BigDecimal valor, int dia,
                               boolean automatico, LocalDate vencimento, ContasFixasService.Situacao situacao,
                               RecurringBill.Tipo tipo, UUID contaId, UUID usuario, boolean compartilhado,
                               Short segundoDia, BigDecimal segundoValor) {

        static BillResponse de(ContasFixasService.ContaDoMes item) {
            RecurringBill conta = item.conta();
            return new BillResponse(conta.getId(), conta.getDescription(), conta.getCategory(), conta.getAmount(),
                    conta.getDueDay(), conta.isAutoLaunch(), item.vencimento(), item.situacao(), conta.getKind(),
                    conta.getAccountId(), conta.getUserId(), conta.isShared(), conta.getSecondDueDay(),
                    conta.getSecondAmount());
        }
    }

    /** As contas no mes pedido (ou no atual), com vencimento e situacao; com tipo RECEITA, as receitas recorrentes. */
    @Operation(summary = "Contas fixas do mês")
    @GetMapping
    public List<BillResponse> listar(@RequestParam(required = false) String mes,
                                     @RequestParam(defaultValue = "GASTO") RecurringBill.Tipo tipo) {
        return servico.doMes(Meses.ouAtual(mes), tipo).stream().map(BillResponse::de).toList();
    }

    @Operation(summary = "Criar conta fixa")
    @ApiResponse(responseCode = "201", description = "Conta fixa criada")
    @PostMapping
    public ResponseEntity<Void> criar(@Valid @RequestBody BillRequest pedido, @AuthUser CurrentUser usuario) {
        servico.criar(pedido.descricao(), pedido.categoria(), pedido.valor(), pedido.dia(),
                Boolean.TRUE.equals(pedido.automatico()), usuario.id(), Boolean.TRUE.equals(pedido.compartilhado()));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Alterar conta fixa")
    @PutMapping("/{id}")
    public ResponseEntity<Void> atualizar(@PathVariable UUID id, @Valid @RequestBody BillRequest pedido,
                                          @AuthUser CurrentUser usuario) {
        servico.atualizar(id, pedido.descricao(), pedido.categoria(), pedido.valor(), pedido.dia(),
                Boolean.TRUE.equals(pedido.automatico()), pedido.compartilhado(), usuario);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Excluir conta fixa")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        servico.excluir(id, usuario);
        return ResponseEntity.noContent().build();
    }

    /** Marca como paga: lanca o gasto do mes. Lancar duas vezes o mesmo mes e 409. */
    @Operation(summary = "Marcar conta fixa como paga no mês")
    @PostMapping("/{id}/pay")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse pagar(@PathVariable UUID id, @RequestParam(required = false) String mes,
                                 @AuthUser CurrentUser usuario) {
        return servico.pagar(id, Meses.ouAtual(mes), usuario.id());
    }
}
