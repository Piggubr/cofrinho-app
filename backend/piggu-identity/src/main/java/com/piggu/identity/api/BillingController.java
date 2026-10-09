package com.piggu.identity.api;

import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.identity.api.dto.PlanoResponse;
import com.piggu.identity.billing.AssinaturaService;
import com.piggu.identity.billing.Periodo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
    public Map<String, String> checkout(@Valid @RequestBody CheckoutRequest pedido, @AuthUser CurrentUser usuario) {
        return Map.of("url", assinaturas.checkout(usuario.id(), pedido.periodo()));
    }

    /** Trocar cartao, ver recibos e cancelar, tao facil quanto assinar. */
    @PostMapping("/portal")
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
    public Map<String, String> portal(@AuthUser CurrentUser usuario) {
        return Map.of("url", assinaturas.portal(usuario.id()));
    }

    /** Desistir nos 7 primeiros dias, com o dinheiro de volta (CDC art. 49). */
    @PostMapping("/refund")
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
    public ResponseEntity<Void> reembolso(@AuthUser CurrentUser usuario) {
        assinaturas.cancelarComReembolso(usuario.id());
        return ResponseEntity.noContent().build();
    }

    /** Aberto: quem prova a origem e a assinatura HMAC do cabecalho, nao um token. */
    @PostMapping("/webhooks/stripe")
    public ResponseEntity<Void> webhookStripe(@RequestBody String corpo,
                                              @RequestHeader(value = "Stripe-Signature", required = false) String assinatura) {
        assinaturas.aplicarWebhook(corpo, assinatura);
        return ResponseEntity.ok().build();
    }

    /**
     * Compra dentro do app (App Store / Google Play), avisada pelo intermediario que valida
     * o recibo com as lojas (RevenueCat). Aberto como o da Stripe: quem prova a origem e o
     * segredo no cabecalho. Sem o segredo configurado, 501.
     */
    @PostMapping("/stores/{loja}/purchases")
    public ResponseEntity<Void> compraNaLoja(@PathVariable String loja, @RequestBody String corpo,
                                             @RequestHeader(value = "Authorization", required = false) String autorizacao) {
        String origem = switch (loja) {
            case "app-store" -> "APP_STORE";
            case "play-store" -> "PLAY_STORE";
            default -> throw new NotFoundException("Loja desconhecida.");
        };
        assinaturas.aplicarAvisoDaLoja(origem, corpo, autorizacao);
        return ResponseEntity.ok().build();
    }
}
