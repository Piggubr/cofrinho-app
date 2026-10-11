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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private final LojaDeApps lojas;
    private final JdbcTemplate jdbc;
    private final int diasDeTeste;

    public AssinaturaService(UserAccountRepository usuarios, FamiliaService familias, ProvedorDePagamento pagamentos,
                             LojaDeApps lojas, JdbcTemplate jdbc,
                             @Value("${piggu.assinatura.dias-de-teste:7}") int diasDeTeste) {
        this.usuarios = usuarios;
        this.familias = familias;
        this.pagamentos = pagamentos;
        this.lojas = lojas;
        this.jdbc = jdbc;
        this.diasDeTeste = diasDeTeste;
    }

    @Transactional(readOnly = true)
    public PlanoResponse plano(UUID usuarioId) {
        UserAccount conta = conta(usuarioId);
        return PlanoResponse.de(familias.daConta(conta), pagamentos.habilitado(), diasDeTesteDe(conta));
    }

    /** Teste gratis uma vez por conta. */
    private int diasDeTesteDe(UserAccount conta) {
        return conta.usouTesteGratis() ? 0 : Math.max(0, diasDeTeste);
    }

    @Transactional(readOnly = true)
    public String checkout(UUID usuarioId, Periodo periodo) {
        UserAccount conta = conta(usuarioId);
        if (familias.daConta(conta).planoVigente() == Plano.PREMIUM) {
            throw new BusinessException("Sua família já é Premium.");
        }
        return pagamentos.abrirCheckout(conta, periodo, diasDeTesteDe(conta));
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
                    if (evento.emTeste()) {
                        conta.marcarTesteGratisUsado();
                    }
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

    /**
     * Aviso de compra feita no app. Idempotente: o mesmo aviso reenviado nao conta duas
     * vezes, e um aviso antigo que chega depois de um mais novo e ignorado pela familia.
     *
     * @param loja APP_STORE ou PLAY_STORE
     */
    @Transactional
    public void aplicarAvisoDaLoja(String loja, String corpo, String autorizacao) {
        if (!lojas.habilitada()) {
            throw new BusinessException("A assinatura pelo app chega junto com os apps.",
                    HttpStatus.NOT_IMPLEMENTED, "EM_BREVE");
        }
        lojas.lerAviso(loja, corpo, autorizacao).ifPresent(aviso -> {
            int novo = jdbc.update("INSERT INTO billing_events (event_id, provider) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    aviso.id(), aviso.origem());
            if (novo == 0) {
                log.info("Aviso da loja repetido ignorado");
                return;
            }
            EventoDeAssinatura evento = aviso.evento();
            usuarios.findById(evento.usuarioId()).ifPresentOrElse(conta -> {
                if (evento.emTeste()) {
                    conta.marcarTesteGratisUsado();
                }
                Household familia = familias.daConta(conta);
                boolean aplicado = familia.aplicarAssinatura(aviso.origem(), evento.premiumAte(), evento.momento(),
                        evento.inicio());
                log.info("Assinatura na loja: conta={} familia={} origem={} plano={} aplicado={}",
                        conta.getId(), familia.getId(), aviso.origem(), familia.planoVigente(), aplicado);
            }, () -> log.warn("Aviso da loja de conta inexistente id={}", evento.usuarioId()));
        });
    }

    private UserAccount conta(UUID usuarioId) {
        return usuarios.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
    }
}
