package com.piggu.identity.billing;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.TBSCertificate;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assina transacoes e avisos como a App Store: raiz, intermediario e folha com as marcas da
 * Apple (OIDs que a biblioteca confere), gerados na hora para nao guardar chave no repositorio.
 */
final class AppleDeTeste {

    static final String BUNDLE = "br.com.piggu.app";
    static final String MENSAL = "br.com.piggu.premium.mensal";

    private static final String OID_INTERMEDIARIO = "1.2.840.113635.100.6.2.1";
    private static final String OID_FOLHA = "1.2.840.113635.100.6.11.1";

    private final KeyPair folha;
    private final List<String> cadeia;
    private final Path raiz;

    AppleDeTeste() {
        try {
            KeyPair raizes = chave();
            KeyPair intermediario = chave();
            folha = chave();
            byte[] raizDer = certificado("Raiz de teste", "Raiz de teste", raizes, raizes.getPrivate(), true, null);
            byte[] intermediarioDer = certificado("Intermediario de teste", "Raiz de teste", intermediario,
                    raizes.getPrivate(), true, OID_INTERMEDIARIO);
            byte[] folhaDer = certificado("Folha de teste", "Intermediario de teste", folha,
                    intermediario.getPrivate(), false, OID_FOLHA);
            Base64.Encoder base64 = Base64.getEncoder();
            cadeia = List.of(base64.encodeToString(folhaDer), base64.encodeToString(intermediarioDer),
                    base64.encodeToString(raizDer));
            raiz = Files.createTempFile("raiz-apple-de-teste", ".cer");
            Files.write(raiz, raizDer);
        } catch (GeneralSecurityException | IOException falha) {
            throw new IllegalStateException(falha);
        }
    }

    /** Para {@code piggu.app-store.raizes}. */
    String raiz() {
        return raiz.toUri().toString();
    }

    /** Transacao de assinatura como o StoreKit 2 entrega ao app. */
    Map<String, Object> transacao(UUID conta, Instant compra, Duration duracao) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("transactionId", "2000000" + compra.toEpochMilli());
        dados.put("originalTransactionId", "1000000123");
        dados.put("bundleId", BUNDLE);
        dados.put("productId", MENSAL);
        dados.put("subscriptionGroupIdentifier", "21000000");
        dados.put("purchaseDate", compra.toEpochMilli());
        dados.put("originalPurchaseDate", compra.toEpochMilli());
        dados.put("expiresDate", compra.plus(duracao).toEpochMilli());
        dados.put("quantity", 1);
        dados.put("type", "Auto-Renewable Subscription");
        dados.put("inAppOwnershipType", "PURCHASED");
        dados.put("signedDate", compra.toEpochMilli());
        dados.put("environment", "Sandbox");
        dados.put("storefront", "BRA");
        dados.put("currency", "BRL");
        dados.put("price", 22890);
        if (conta != null) {
            dados.put("appAccountToken", conta.toString());
        }
        return dados;
    }

    /** App Store Server Notification V2 com a transacao dentro, ja assinada. */
    String aviso(String tipo, String subtipo, Map<String, Object> transacao, Instant momento) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("bundleId", BUNDLE);
        dados.put("environment", "Sandbox");
        dados.put("signedTransactionInfo", assinar(transacao));
        Map<String, Object> aviso = new LinkedHashMap<>();
        aviso.put("notificationType", tipo);
        if (subtipo != null) {
            aviso.put("subtype", subtipo);
        }
        aviso.put("notificationUUID", UUID.randomUUID().toString());
        aviso.put("version", "2.0");
        aviso.put("signedDate", momento.toEpochMilli());
        aviso.put("data", dados);
        return assinar(aviso);
    }

    String assinar(Map<String, Object> corpo) {
        return JWT.create()
                .withHeader(Map.of("x5c", cadeia))
                .withPayload(corpo)
                .sign(Algorithm.ECDSA256((ECPublicKey) folha.getPublic(), (ECPrivateKey) folha.getPrivate()));
    }

    private static KeyPair chave() throws GeneralSecurityException {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("EC");
        gerador.initialize(new ECGenParameterSpec("secp256r1"));
        return gerador.generateKeyPair();
    }

    private static byte[] certificado(String nome, String emissor, KeyPair dono, PrivateKey chaveDoEmissor,
                                      boolean autoridade, String marcaDaApple) throws GeneralSecurityException {
        try {
            AlgorithmIdentifier algoritmo = new AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256);
            V3TBSCertificateGenerator dados = new V3TBSCertificateGenerator();
            dados.setSerialNumber(new ASN1Integer(System.nanoTime()));
            dados.setIssuer(new X500Name("CN=" + emissor));
            dados.setSubject(new X500Name("CN=" + nome));
            dados.setStartDate(new Time(Date.from(Instant.now().minus(Duration.ofDays(400)))));
            dados.setEndDate(new Time(Date.from(Instant.now().plus(Duration.ofDays(3650)))));
            dados.setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(dono.getPublic().getEncoded()));
            dados.setSignature(algoritmo);
            ExtensionsGenerator extensoes = new ExtensionsGenerator();
            extensoes.addExtension(Extension.basicConstraints, true, new BasicConstraints(autoridade));
            if (autoridade) {
                extensoes.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            }
            if (marcaDaApple != null) {
                extensoes.addExtension(new ASN1ObjectIdentifier(marcaDaApple), false, DERNull.INSTANCE);
            }
            dados.setExtensions(extensoes.generate());
            TBSCertificate corpo = dados.generateTBSCertificate();

            Signature assinatura = Signature.getInstance("SHA256withECDSA");
            assinatura.initSign(chaveDoEmissor);
            assinatura.update(corpo.getEncoded(ASN1Encoding.DER));
            ASN1EncodableVector certificado = new ASN1EncodableVector();
            certificado.add(corpo);
            certificado.add(algoritmo);
            certificado.add(new DERBitString(assinatura.sign()));
            return new DERSequence(certificado).getEncoded(ASN1Encoding.DER);
        } catch (IOException falha) {
            throw new UncheckedIOException(falha);
        }
    }
}
