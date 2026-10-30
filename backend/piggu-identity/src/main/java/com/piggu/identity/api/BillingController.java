package com.piggu.identity.api;

import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.identity.api.dto.PlanoResponse;
import com.piggu.identity.billing.AssinaturaService;
import com.piggu.identity.billing.Periodo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Plano Premium: tela de planos, pagamento no site e compra dentro dos apps. */
@Tag(name = "Premium", description = "Plano, pagamento no site e compra dentro dos apps.")
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final AssinaturaService assinaturas;

    public BillingController(AssinaturaService assinaturas) {
        this.assinaturas = assinaturas;
    }

    /** @param campanha codigo de promocao que liberou o teste (vem do link /plano?promo=); opcional */
    public record CheckoutRequest(@NotNull(message = "Escolha mensal ou anual.") Periodo periodo,
                                  @Size(max = 40) String campanha) {
    }

    /** @param transacao o {@code jwsRepresentation} da transacao verificada pelo StoreKit 2 */
    public record CompraNaAppleRequest(@NotBlank(message = "Mande a transação assinada pela Apple.")
                                       @Size(max = 20_000) String transacao) {
    }

    /** Corpo das App Store Server Notifications V2. */
    public record AvisoDaAppleRequest(@NotBlank @Size(max = 100_000) String signedPayload) {
    }

    /**
     * @param produto       o produto comprado no Google Play
     * @param purchaseToken o token que o Google Play Billing devolveu na compra
     */
    public record CompraNoGoogleRequest(@NotBlank @Pattern(regexp = "premium_(mensal|anual)", message = "Produto desconhecido.")
                                        String produto,
                                        @NotBlank(message = "Mande o token da compra do Google Play.")
                                        @Size(max = 4_000) String purchaseToken) {
    }

    /** Push do Pub/Sub com o aviso em tempo real do Google Play (RTDN). */
    public record AvisoDoGoogleRequest(@NotNull @Valid MensagemDoPubSub message, @Size(max = 500) String subscription) {
    }

    /** @param data o aviso do Google Play em JSON, codificado em base64 */
    public record MensagemDoPubSub(@NotBlank @Size(max = 100_000) String data, String messageId, String publishTime) {
    }

    @Operation(summary = "Plano atual e opções de assinatura")
    @GetMapping("/plan")
    public PlanoResponse plano(@AuthUser CurrentUser usuario,
                               @RequestParam(required = false) @Size(max = 40) String campanha) {
        return assinaturas.plano(usuario.id(), campanha);
    }

    /** Devolve a pagina de pagamento do provedor; o front so redireciona. */
    @Operation(summary = "Abrir o pagamento do Premium no site")
    @PostMapping("/checkout")
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
    public Map<String, String> checkout(@Valid @RequestBody CheckoutRequest pedido, @AuthUser CurrentUser usuario) {
        return Map.of("url", assinaturas.checkout(usuario.id(), pedido.periodo(), pedido.campanha()));
    }

    /** Trocar cartao, ver recibos e cancelar, tao facil quanto assinar. */
    @Operation(summary = "Abrir o portal da assinatura (cartão, recibos, cancelar)")
    @PostMapping("/portal")
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
    public Map<String, String> portal(@AuthUser CurrentUser usuario) {
        return Map.of("url", assinaturas.portal(usuario.id()));
    }

    /**
     * Desistir em ate 7 dias da primeira cobranca, com o dinheiro de volta (CDC art. 49).
     * {@code reembolsado} falso: cancelou no teste, sem nada a devolver.
     */
    @Operation(summary = "Desistir em até 7 dias com reembolso")
    @PostMapping("/refund")
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
    public Map<String, Boolean> reembolso(@AuthUser CurrentUser usuario) {
        return Map.of("reembolsado", assinaturas.cancelarComReembolso(usuario.id()));
    }

    /**
     * Compra feita no iPhone pelo StoreKit 2. O app manda a transacao assinada pela Apple logo
     * depois de comprar ou ao restaurar; devolve o plano ja atualizado.
     */
    @Operation(summary = "Confirmar a assinatura comprada no iPhone (transação assinada pela Apple)",
            description = "409 com o código OUTRA_CONTA quando a compra foi feita por outra conta do Piggu.")
    @PostMapping("/app-store/transactions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
    public PlanoResponse compraNaApple(@Valid @RequestBody CompraNaAppleRequest pedido, @AuthUser CurrentUser usuario) {
        return assinaturas.aplicarCompraDaApple(usuario.id(), pedido.transacao());
    }

    /** Aberto: quem prova a origem e a assinatura da Apple no proprio corpo (JWS). */
    @Operation(summary = "Aviso da App Store, Server Notifications V2 (servidor para servidor, não é para o app)")
    @ApiResponse(responseCode = "200", description = "Aviso recebido")
    @SecurityRequirements
    @PostMapping("/webhooks/app-store")
    public ResponseEntity<Void> webhookAppStore(@Valid @RequestBody AvisoDaAppleRequest aviso) {
        assinaturas.aplicarAvisoDaApple(aviso.signedPayload());
        return ResponseEntity.ok().build();
    }

    /**
     * Compra feita no Android pelo Google Play Billing. O app manda o purchaseToken logo depois
     * de comprar ou ao restaurar; devolve o plano ja atualizado.
     */
    @Operation(summary = "Confirmar a assinatura comprada no Android (token da compra no Google Play)",
            description = "409 com o código OUTRA_CONTA quando a compra foi feita por outra conta do Piggu.")
    @PostMapping("/google-play/purchases")
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
    public PlanoResponse compraNoGoogle(@Valid @RequestBody CompraNoGoogleRequest pedido, @AuthUser CurrentUser usuario) {
        return assinaturas.aplicarCompraDoGoogle(usuario.id(), pedido.purchaseToken());
    }

    /** Aberto: quem prova a origem e o token OIDC que o Google poe no push do Pub/Sub. */
    @Operation(summary = "Aviso do Google Play em tempo real, pelo push do Pub/Sub (servidor para servidor, não é para o app)")
    @ApiResponse(responseCode = "200", description = "Aviso recebido")
    @SecurityRequirements
    @PostMapping("/webhooks/google-play")
    public ResponseEntity<Void> webhookGooglePlay(@Valid @RequestBody AvisoDoGoogleRequest aviso,
                                                  @RequestHeader(value = "Authorization", required = false) String autorizacao) {
        assinaturas.aplicarAvisoDoGoogle(autorizacao, aviso.message().data());
        return ResponseEntity.ok().build();
    }

    /** Aberto: quem prova a origem e a assinatura HMAC do cabecalho, nao um token. */
    @Operation(summary = "Aviso da Stripe (servidor para servidor, não é para o app)")
    @ApiResponse(responseCode = "200", description = "Aviso recebido")
    @SecurityRequirements
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
    @Operation(summary = "Aviso de compra nas lojas pelo RevenueCat (servidor para servidor, não é para o app)")
    @ApiResponse(responseCode = "200", description = "Aviso recebido")
    @SecurityRequirements
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
