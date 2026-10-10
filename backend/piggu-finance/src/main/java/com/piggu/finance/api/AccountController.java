package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Meses;
import com.piggu.finance.domain.ContasECartoes;
import com.piggu.finance.domain.PaymentAccount;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Contas e cartoes da familia, e a fatura do cartao. */
@RestController
@RequestMapping("/api/accounts")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR', 'PARCEIRO')")
public class AccountController {

    private final ContasECartoes servico;

    public AccountController(ContasECartoes servico) {
        this.servico = servico;
    }

    public record AccountRequest(
            @NotBlank(message = "Digite o nome.") @Size(max = 60) String nome,
            @NotNull(message = "Escolha conta ou cartao.") PaymentAccount.Tipo tipo,
            @Min(value = 1, message = "Dia invalido.") @Max(value = 31, message = "Dia invalido.") Integer fechamento,
            @Min(value = 1, message = "Dia invalido.") @Max(value = 31, message = "Dia invalido.") Integer vencimento) {
    }

    /** Cada conta; no cartao, a fatura aberta e a fechada que ainda vai vencer. */
    @GetMapping
    public List<ContasECartoes.Conta> listar() {
        return servico.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContasECartoes.Conta criar(@Valid @RequestBody AccountRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.criar(pedido.nome(), pedido.tipo(), pedido.fechamento(), pedido.vencimento(), usuario.email());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        servico.excluir(id);
        return ResponseEntity.noContent().build();
    }

    /** Fatura que fecha no mes informado (AAAA-MM), com os gastos. */
    @GetMapping("/{id}/statement")
    public ContasECartoes.FaturaComGastos fatura(@PathVariable UUID id, @RequestParam(required = false) String mes) {
        return servico.fatura(id, Meses.ouAtual(mes));
    }
}
