package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Meses;
import com.piggu.finance.domain.ContasECartoes;
import com.piggu.finance.domain.PaymentAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.UUID;

/** Contas e cartoes da familia, e a fatura do cartao. */
@Tag(name = "Contas e cartões", description = "Contas, cartões e faturas.")
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final ContasECartoes servico;

    public AccountController(ContasECartoes servico) {
        this.servico = servico;
    }

    public record AccountRequest(
            @NotBlank(message = "Digite o nome.") @Size(max = 60) String nome,
            @NotNull(message = "Escolha conta ou cartao.") PaymentAccount.Tipo tipo,
            @Min(value = 1, message = "Dia invalido.") @Max(value = 31, message = "Dia invalido.") Integer fechamento,
            @Min(value = 1, message = "Dia invalido.") @Max(value = 31, message = "Dia invalido.") Integer vencimento,
            Boolean compartilhado) {
    }

    public record CompartilhamentoRequest(@NotNull Boolean compartilhado) {
    }

    /** Cada conta; no cartao, a fatura aberta e a fechada que ainda vai vencer. */
    @Operation(summary = "Listar contas e cartões")
    @GetMapping
    public List<ContasECartoes.Conta> listar() {
        return servico.listar();
    }

    @Operation(summary = "Criar conta ou cartão")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContasECartoes.Conta criar(@Valid @RequestBody AccountRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.criar(pedido.nome(), pedido.tipo(), pedido.fechamento(), pedido.vencimento(), usuario.id(),
                Boolean.TRUE.equals(pedido.compartilhado()));
    }

    /** Conta ou cartao do casal: so quem criou liga ou desliga. */
    @Operation(summary = "Compartilhar ou não a conta com o casal")
    @PutMapping("/{id}/sharing")
    public ResponseEntity<Void> compartilhar(@PathVariable UUID id, @Valid @RequestBody CompartilhamentoRequest pedido,
                                             @AuthUser CurrentUser usuario) {
        servico.compartilhar(id, pedido.compartilhado(), usuario);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Excluir conta ou cartão")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        servico.excluir(id, usuario);
        return ResponseEntity.noContent().build();
    }

    /** Fatura que fecha no mes informado (AAAA-MM), com os gastos. */
    @Operation(summary = "Fatura do cartão no mês")
    @GetMapping("/{id}/statement")
    public ContasECartoes.FaturaComGastos fatura(@PathVariable UUID id, @RequestParam(required = false) String mes) {
        return servico.fatura(id, Meses.ouAtual(mes));
    }

    /** So tira a fatura do "a pagar", com as anteriores; nao lanca gasto. Marcar de novo e 204 tambem. */
    @Operation(summary = "Marcar a fatura do cartão como paga")
    @PutMapping("/{id}/statement/paid")
    public ResponseEntity<Void> pagarFatura(@PathVariable UUID id, @RequestParam String mes) {
        servico.pagarFatura(id, Meses.ouAtual(mes));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Desfazer o pagamento da fatura do cartão")
    @DeleteMapping("/{id}/statement/paid")
    public ResponseEntity<Void> desfazerPagamento(@PathVariable UUID id, @RequestParam String mes) {
        servico.desfazerPagamento(id, Meses.ouAtual(mes));
        return ResponseEntity.noContent().build();
    }
}
