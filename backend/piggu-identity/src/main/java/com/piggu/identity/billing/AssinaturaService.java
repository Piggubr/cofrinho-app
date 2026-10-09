package com.piggu.identity.billing;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.security.Plano;
import com.piggu.identity.api.dto.PlanoResponse;
import com.piggu.identity.domain.FamiliaService;
import com.piggu.identity.domain.Household;
import com.piggu.identity.domain.UserAccount;
import com.piggu.identity.domain.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Plano da conta: mostrar, mandar para o pagamento e aplicar o que o provedor avisou.
 *
 * <p>O Premium muda so por webhook. Voltar da pagina de pagamento nao concede nada:
 * qualquer um poderia abrir a URL de sucesso.</p>
 */
@Service
public class AssinaturaService {

    private static final Logger log = LoggerFactory.getLogger(AssinaturaService.class);

    /** CDC art. 49: sete dias para desistir de compra feita fora do estabelecimento. */
    public static final Duration PRAZO_DE_ARREPENDIMENTO = Duration.ofDays(7);

    private final UserAccountRepository usuarios;
    private final FamiliaService familias;
    private final ProvedorDePagamento pagamentos;

    public AssinaturaService(UserAccountRepository usuarios, FamiliaService familias, ProvedorDePagamento pagamentos) {
        this.usuarios = usuarios;
        this.familias = familias;
        this.pagamentos = pagamentos;
    }

    @Transactional(readOnly = true)
    public PlanoResponse plano(UUID usuarioId) {
        return PlanoResponse.de(familias.daConta(conta(usuarioId)), pagamentos.habilitado());
    }

    @Transactional(readOnly = true)
    public String checkout(UUID usuarioId, Periodo periodo) {
        UserAccount conta = conta(usuarioId);
        if (familias.daConta(conta).planoVigente() == Plano.PREMIUM) {
            throw new BusinessException("Sua família já é Premium.");
        }
        return pagamentos.abrirCheckout(conta, periodo);
    }

    @Transactional(readOnly = true)
    public String portal(UUID usuarioId) {
        return pagamentos.abrirPortal(conta(usuarioId));
    }

    /**
     * Aviso do provedor do site. Quem assinou e uma conta; o Premium vale para a familia
     * dela. Evento de conta que nao existe mais e ignorado sem erro.
     */
    @Transactional
    public void aplicarWebhook(String corpo, String assinatura) {
        pagamentos.lerWebhook(corpo, assinatura).ifPresent(evento ->
                usuarios.findById(evento.usuarioId()).ifPresentOrElse(conta -> {
                    conta.lembrarClienteNoProvedor(evento.clienteNoProvedor());
                    Household familia = familias.daConta(conta);
                    boolean aplicado = familia.aplicarAssinatura("WEB", evento.premiumAte(), evento.momento(),
                            evento.inicio());
                    log.info("Assinatura web: conta={} familia={} plano={} aplicado={}",
                            conta.getId(), familia.getId(), familia.planoVigente(), aplicado);
                }, () -> log.warn("Assinatura web de conta inexistente id={}", evento.usuarioId())));
    }

    /**
     * Direito de arrependimento (CDC art. 49): nos 7 primeiros dias, quem assinou cancela
     * e recebe tudo de volta. O Premium sai na hora aqui; o aviso da Stripe confirma depois.
     */
    @Transactional
    public void cancelarComReembolso(UUID usuarioId) {
        UserAccount conta = conta(usuarioId);
        Household familia = familias.daConta(conta);
        if (conta.getStripeCustomerId() == null || !"WEB".equals(familia.getPlanSource())) {
            throw new BusinessException("O reembolso pelo app vale para quem assinou pelo site.");
        }
        pagamentos.cancelarComReembolso(conta.getStripeCustomerId(), PRAZO_DE_ARREPENDIMENTO);
        familia.aplicarAssinatura("WEB", null, Instant.now());
        log.info("Arrependimento: conta={} familia={}", conta.getId(), familia.getId());
    }

    private UserAccount conta(UUID usuarioId) {
        return usuarios.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
    }
}
