package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.finance.api.dto.DepositRequest;
import com.piggu.finance.api.dto.DepositResponse;
import com.piggu.finance.api.dto.PiggyBankResponse;
import com.piggu.finance.domain.PiggyBankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.UUID;

/**
 * Cofrinho.
 *
 * <p>O MEMBRO deposita e ve o proprio total. A separacao do que cada perfil enxerga
 * esta em PiggyBankService, nao aqui, para que a regra valha em qualquer caminho de
 * chamada.</p>
 */
@Tag(name = "Cofrinho", description = "Saldo e depósitos do cofrinho.")
@RestController
@RequestMapping("/api/piggy-bank")
public class PiggyBankController {

    private final PiggyBankService servico;

    public PiggyBankController(PiggyBankService servico) {
        this.servico = servico;
    }

    @Operation(summary = "Saldo e depósitos do cofrinho")
    @GetMapping
    public PiggyBankResponse consultar(@AuthUser CurrentUser usuario) {
        return servico.consultar(usuario);
    }

    @Operation(summary = "Depositar no cofrinho")
    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public DepositResponse depositar(@Valid @RequestBody DepositRequest pedido,
                                     @AuthUser CurrentUser usuario) {
        return servico.depositar(pedido, usuario);
    }

    @Operation(summary = "Excluir depósito")
    @DeleteMapping("/deposits/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        servico.excluir(id, usuario);
        return ResponseEntity.noContent().build();
    }
}
