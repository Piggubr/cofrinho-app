package com.piggu.identity.api;

import com.piggu.common.error.BusinessException;
import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.identity.api.dto.PlanoResponse;
import com.piggu.identity.billing.AssinaturaService;
import com.piggu.identity.billing.Periodo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Plano Premium: tela de planos, pagamento no site e compra dentro dos apps. */
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final AssinaturaService assinaturas;

    public BillingController(AssinaturaService assinaturas) {
        this.assinaturas = assinaturas;
    }

    public record CheckoutRequest(@NotNull(message = "Escolha mensal ou anual.") Periodo periodo) {
    }

    @GetMapping("/plan")
    public PlanoResponse plano(@AuthUser CurrentUser usuario) {
        return assinaturas.plano(usuario.id());
    }

    /** Devolve a pagina de pagamento do provedor; o front so redireciona. */
    @PostMapping("/checkout")
    @PreAuthorize("hasAnyRole('ADMIN', 'BEATRIZ')")
    public Map<String, String> checkout(@Valid @RequestBody CheckoutRequest pedido, @AuthUser CurrentUser usuario) {
        return Map.of("url", assinaturas.checkout(usuario.id(), pedido.periodo()));
    }

    /** Trocar cartao, ver recibos e cancelar, tao facil quanto assinar. */
    @PostMapping("/portal")
    @PreAuthorize("hasAnyRole('ADMIN', 'BEATRIZ')")
    public Map<String, String> portal(@AuthUser CurrentUser usuario) {
        return Map.of("url", assinaturas.portal(usuario.id()));
    }

    /** Aberto: quem prova a origem e a assinatura HMAC do cabecalho, nao um token. */
    @PostMapping("/webhooks/stripe")
    public ResponseEntity<Void> webhookStripe(@RequestBody String corpo,
                                              @RequestHeader(value = "Stripe-Signature", required = false) String assinatura) {
        assinaturas.aplicarWebhook(corpo, assinatura);
        return ResponseEntity.ok().build();
    }

    /**
     * Compra dentro do app (App Store / Google Play), que as lojas exigem para assinatura
     * digital vendida no app. Ainda sem apps publicados: responder 501 e mais honesto do
     * que fingir uma validacao de recibo.
     */
    @PostMapping("/stores/{loja}/purchases")
    @PreAuthorize("hasAnyRole('ADMIN', 'BEATRIZ')")
    public ResponseEntity<Void> compraNaLoja(@PathVariable String loja) {
        throw new BusinessException("A assinatura pelo app chega junto com os apps.",
                HttpStatus.NOT_IMPLEMENTED, "EM_BREVE");
    }
}
