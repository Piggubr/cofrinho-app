package com.piggu.identity.billing;

import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.JWSRenewalInfoDecodedPayload;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.OfferDiscountType;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.apple.itunes.storekit.model.Type;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.apple.itunes.storekit.verification.VerificationException;
import com.apple.itunes.storekit.verification.VerificationStatus;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.error.UpstreamException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Assinatura comprada no iPhone pelo StoreKit 2, conferida com a biblioteca oficial da Apple
 * (app-store-server-library), sem intermediario (D-094).
 *
 * <p>Tudo o que chega e um JWS assinado pela Apple: a cadeia de certificados ate a raiz da
 * Apple, o bundle id e o ambiente sao conferidos antes de ler qualquer campo. O dono da compra
 * e o {@code appAccountToken}, que o app preenche com o id da conta Piggu na hora de comprar.</p>
 *
 * <p>Sandbox e sempre aceito: a revisao da Apple compra no sandbox contra o servidor de
 * producao. O ambiente do Xcode nao tem assinatura de verdade, entao so entra com
 * {@code piggu.app-store.aceitar-xcode=true}, ligado a mao no desenvolvimento (D-151).</p>
 */
@Component
public class AppStoreLoja {

    /** @param id identificador unico do aviso (notificationUUID), para processar cada um uma vez so */
    public record AvisoDaApple(String id, EventoDeAssinatura evento) {
    }

    private final List<SignedDataVerifier> verificadores = new ArrayList<>();

    public AppStoreLoja(@Value("${piggu.app-store.raizes:classpath:apple/AppleRootCA-G3.cer}") Resource[] raizes,
                        @Value("${piggu.app-store.bundle-id:br.com.piggu.app}") String bundleId,
                        @Value("${piggu.app-store.app-apple-id:}") String appAppleId,
                        @Value("${piggu.app-store.checar-online:true}") boolean checarOnline,
                        @Value("${piggu.app-store.aceitar-xcode:false}") boolean aceitarXcode) {
        List<byte[]> certificados = ler(raizes);
        if (appAppleId != null && !appAppleId.isBlank()) {
            verificadores.add(verificador(certificados, bundleId, Long.valueOf(appAppleId.trim()), Environment.PRODUCTION, checarOnline));
        }
        verificadores.add(verificador(certificados, bundleId, null, Environment.SANDBOX, checarOnline));
        if (aceitarXcode) {
            verificadores.add(verificador(certificados, bundleId, null, Environment.XCODE, checarOnline));
        }
    }

    /**
     * Transacao que o app mandou logo depois da compra (ou ao restaurar).
     *
     * @throws BusinessException quando a Apple nao assinou isto, ou nao e uma assinatura do Piggu
     */
    public EventoDeAssinatura lerTransacao(String transacao) {
        JWSTransactionDecodedPayload compra = conferir(transacao, (verificador, jws) -> verificador.verifyAndDecodeTransaction(jws),
                () -> new BusinessException("Não deu para confirmar a compra com a Apple. Tente restaurar as compras."));
        if (compra.getType() != Type.AUTO_RENEWABLE_SUBSCRIPTION) {
            throw new BusinessException("Esta compra não é uma assinatura do Piggu Premium.");
        }
        return traduzir(compra, null, compra.getSignedDate());
    }

    /**
     * App Store Server Notification V2: renovacao, cancelamento, expiracao, reembolso.
     *
     * @return vazio quando o aviso e valido mas nao traz assinatura (TEST, resumo, etc.)
     * @throws UnauthorizedException quando a Apple nao assinou o aviso
     */
    public Optional<AvisoDaApple> lerAviso(String signedPayload) {
        ResponseBodyV2DecodedPayload aviso = conferir(signedPayload, (verificador, jws) -> verificador.verifyAndDecodeNotification(jws),
                () -> new UnauthorizedException("Aviso da App Store sem assinatura válida."));
        if (aviso.getData() == null || aviso.getData().getSignedTransactionInfo() == null) {
            return Optional.empty();
        }
        JWSTransactionDecodedPayload compra = conferir(aviso.getData().getSignedTransactionInfo(),
                (verificador, jws) -> verificador.verifyAndDecodeTransaction(jws),
                () -> new UnauthorizedException("Transação do aviso sem assinatura válida."));
        if (compra.getType() != Type.AUTO_RENEWABLE_SUBSCRIPTION) {
            return Optional.empty();
        }
        String renovacaoAssinada = aviso.getData().getSignedRenewalInfo();
        JWSRenewalInfoDecodedPayload renovacao = renovacaoAssinada == null ? null
                : conferir(renovacaoAssinada, (verificador, jws) -> verificador.verifyAndDecodeRenewalInfo(jws),
                        () -> new UnauthorizedException("Renovação do aviso sem assinatura válida."));
        return Optional.of(new AvisoDaApple(aviso.getNotificationUUID(), traduzir(compra, renovacao, aviso.getSignedDate())));
    }

    /**
     * Reembolso ou revogacao encerram na hora; no periodo de carencia (cobranca falhou e a Apple
     * ainda tenta) o Premium vai ate o fim da carencia.
     */
    static EventoDeAssinatura traduzir(JWSTransactionDecodedPayload compra, JWSRenewalInfoDecodedPayload renovacao, Long momento) {
        boolean revogada = compra.getRevocationDate() != null;
        Instant ate = revogada ? null : instante(compra.getExpiresDate());
        Instant carencia = renovacao == null ? null : instante(renovacao.getGracePeriodExpiresDate());
        if (!revogada && carencia != null && (ate == null || carencia.isAfter(ate))) {
            ate = carencia;
        }
        return new EventoDeAssinatura(compra.getAppAccountToken(), null, ate,
                momento == null ? Instant.now() : Instant.ofEpochMilli(momento),
                instante(compra.getOriginalPurchaseDate()),
                compra.getOfferDiscountType() == OfferDiscountType.FREE_TRIAL,
                TipoDePlano.doProduto(compra.getProductId()), null, null, null);
    }

    private interface Conferencia<T> {
        T aplicar(SignedDataVerifier verificador, String jws) throws VerificationException;
    }

    /** Tenta cada ambiente aceito; so a falha de rede da Apple (OCSP) pede para tentar de novo. */
    private <T> T conferir(String jws, Conferencia<T> conferencia, Supplier<RuntimeException> recusa) {
        boolean tentarDeNovo = false;
        for (SignedDataVerifier verificador : verificadores) {
            try {
                return conferencia.aplicar(verificador, jws);
            } catch (VerificationException falha) {
                tentarDeNovo |= falha.getStatus() == VerificationStatus.RETRYABLE_VERIFICATION_FAILURE;
            }
        }
        if (tentarDeNovo) {
            throw new UpstreamException("Não deu para falar com a Apple agora. Tente de novo em instantes.");
        }
        throw recusa.get();
    }

    private static SignedDataVerifier verificador(List<byte[]> raizes, String bundleId, Long appAppleId,
                                                  Environment ambiente, boolean checarOnline) {
        Set<InputStream> certificados = raizes.stream().map(ByteArrayInputStream::new).collect(Collectors.toSet());
        // Sem assinatura de verdade no Xcode: nao ha o que checar online.
        return new SignedDataVerifier(certificados, bundleId, appAppleId, ambiente, checarOnline && ambiente != Environment.XCODE);
    }

    private static List<byte[]> ler(Resource[] raizes) {
        List<byte[]> certificados = new ArrayList<>();
        for (Resource raiz : raizes) {
            try (InputStream entrada = raiz.getInputStream()) {
                certificados.add(entrada.readAllBytes());
            } catch (IOException falha) {
                throw new UncheckedIOException("Certificado raiz da Apple ilegível: " + raiz, falha);
            }
        }
        return certificados;
    }

    private static Instant instante(Long milissegundos) {
        return milissegundos == null ? null : Instant.ofEpochMilli(milissegundos);
    }
}
