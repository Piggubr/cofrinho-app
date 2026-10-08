package com.piggu.banking.api;

import com.piggu.banking.api.dto.BankAccountResponse;
import com.piggu.banking.api.dto.ConnectTokenResponse;
import com.piggu.banking.api.dto.RegisterItemRequest;
import com.piggu.banking.domain.BankingService;
import com.piggu.common.dados.Consentimentos;
import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contas bancarias conectadas por Open Finance. Cada usuario ve so os proprios bancos. */
@RestController
@RequestMapping("/api/banking")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
public class BankingController {

    private final BankingService servico;
    private final Consentimentos consentimentos;

    public BankingController(BankingService servico, Consentimentos consentimentos) {
        this.servico = servico;
        this.consentimentos = consentimentos;
    }

    /** O consentimento chega no mesmo pedido que abre a conexao com o banco. */
    public record ConsentimentoRequest(Boolean autorizo, @Size(max = 20) String versaoDoAviso) {
    }

    /** Open Finance e opcional: o front so mostra o card quando isto diz que esta ligado. */
    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("habilitado", servico.habilitado());
    }

    /**
     * Cada banco conectado e um compartilhamento novo: a autorizacao vem a cada vez,
     * antes de o widget da Pluggy sequer carregar.
     */
    @PostMapping("/connect-token")
    public ConnectTokenResponse connectToken(@Valid @RequestBody(required = false) ConsentimentoRequest pedido,
                                             @AuthUser CurrentUser usuario) {
        usuario.exigirPremium("Conectar bancos pelo Open Finance");
        consentimentos.exigirAgora(usuario, "PLUGGY",
                "Para conectar um banco, autorize o compartilhamento dos seus dados bancarios com a Pluggy.",
                pedido == null ? null : pedido.autorizo(), pedido == null ? null : pedido.versaoDoAviso());
        return servico.gerarConnectToken(usuario);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public List<BankAccountResponse> registrar(@Valid @RequestBody RegisterItemRequest pedido,
                                               @AuthUser CurrentUser usuario) {
        usuario.exigirPremium("Conectar bancos pelo Open Finance");
        return servico.registrar(pedido.itemId(), usuario);
    }

    @GetMapping("/accounts")
    public List<BankAccountResponse> contas(@AuthUser CurrentUser usuario) {
        return servico.listar(usuario);
    }

    /** Sempre gratis, inclusive depois que o Premium vence. */
    @DeleteMapping("/connections/{id}")
    public List<BankAccountResponse> desconectar(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        return servico.desconectar(id, usuario);
    }

    @PostMapping("/sync")
    public List<BankAccountResponse> sincronizar(@AuthUser CurrentUser usuario) {
        usuario.exigirPremium("Atualizar os saldos dos bancos");
        return servico.sincronizarTudo(usuario);
    }
}
