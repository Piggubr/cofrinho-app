package com.piggu.identity.billing;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.error.UpstreamException;
import com.piggu.common.security.Plano;
import com.piggu.identity.api.dto.PlanoResponse;
import com.piggu.identity.avisos.CanalDeEmail;
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
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final UserAccountRepository usuarios;
    private final FamiliaService familias;
    private final StripePagamentos pagamentos;

    private final AppStoreLoja appStore;
    private final GooglePlayLoja googlePlay;
    private final JdbcTemplate jdbc;
    private final TestesGratis testes;
    private final CanalDeEmail email;
    private final int diasDeTeste;
    private final boolean openFinance;

    public AssinaturaService(UserAccountRepository usuarios, FamiliaService familias, StripePagamentos pagamentos,
                             AppStoreLoja appStore, GooglePlayLoja googlePlay, JdbcTemplate jdbc, TestesGratis testes, CanalDeEmail email,
                             @Value("${piggu.assinatura.dias-de-teste:14}") int diasDeTeste,
                             @Value("${piggu.open-finance.ligado:false}") boolean openFinance) {
        this.openFinance = openFinance;
        this.usuarios = usuarios;
        this.familias = familias;
        this.pagamentos = pagamentos;
        this.appStore = appStore;
        this.googlePlay = googlePlay;
        this.jdbc = jdbc;
        this.testes = testes;
        this.email = email;
        this.diasDeTeste = diasDeTeste;
    }

    /** @param campanha codigo de promocao do link; invalido mostra o teste normal (o checkout explica o erro) */
    @Transactional(readOnly = true)
    public PlanoResponse plano(UUID usuarioId, String campanha) {
        UserAccount conta = conta(usuarioId);
        int dias;
        try {
            dias = diasDeTesteDe(conta, campanha);
        } catch (BusinessException campanhaInvalida) {
            dias = diasDeTesteDe(conta, null);
        }
        return PlanoResponse.de(conta, familias.daConta(conta), pagamentos.habilitado(),
                pagamentos.familiaHabilitado(), pagamentos.casalHabilitado(), dias, openFinance);
    }

    /**
     * Teste gratis uma vez por pessoa: por conta e, para quem informou o CPF, por CPF (uma conta
     * ja excluida com o mesmo CPF que testou tira o teste desta; ativa nao ha, o CPF e unico). A
     * busca e pelo HMAC do CPF.
     */
    private boolean jaTestou(UserAccount conta) {
        return conta.usouTesteGratis() || (conta.temCpf() && testes.cpfDeContaExcluidaTestou(conta.getCpfIndice()));
    }

    /**
     * Dias de teste ao assinar. Uma campanha valida troca os dias e, se for para quem ja
     * testou, libera o teste de novo; cada conta usa cada campanha uma vez.
     *
     * @throws BusinessException campanha que nao existe, acabou, lotou ou ja foi usada
     */
    private int diasDeTesteDe(UserAccount conta, String codigo) {
        String campanha = TestesGratis.normalizar(codigo);
        if (campanha == null) {
            return jaTestou(conta) ? 0 : Math.max(0, diasDeTeste);
        }
        TestesGratis.Campanha valida = testes.campanha(campanha).filter(TestesGratis.Campanha::valeAgora)
                .orElseThrow(() -> new BusinessException("Esta promoção não existe ou já acabou."));
        if (testes.usouCampanha(conta.getId(), valida.codigo())) {
            throw new BusinessException("Você já usou esta promoção.");
        }
        if (!valida.paraQuemJaTestou() && jaTestou(conta)) {
            throw new BusinessException("Esta promoção é para quem ainda não usou o teste grátis.");
        }
        return valida.diasDeTeste();
    }

    @Transactional(readOnly = true)
    public String checkout(UUID usuarioId, Periodo periodo, String campanha) {
        UserAccount conta = conta(usuarioId);
        Household familia = familias.daConta(conta);
        // Uma assinatura por casa: duas se sobreporiam na mesma familia.
        if (familia.planoVigente() == Plano.PREMIUM) {
            throw new BusinessException(conta.getId().equals(familia.getSubscriberId())
                    ? "Você já é Premium. Para trocar de plano, use Gerenciar assinatura."
                    : "Já há uma assinatura nesta família. Quem assinou pode trocar para o plano casal ou família.");
        }
        if (periodo.familia() && familias.pessoasNaCasa(familia.getId()) > Periodo.PESSOAS_NO_FAMILIA) {
            throw new BusinessException("O plano família cobre até " + Periodo.PESSOAS_NO_FAMILIA
                    + " pessoas. Tire alguém da família ou cancele convites antes de assinar.");
        }
        int dias = diasDeTesteDe(conta, campanha);
        return pagamentos.abrirCheckout(conta, periodo, dias, TestesGratis.normalizar(campanha));
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
                        if (evento.campanha() != null) {
                            testes.registrarUso(conta.getId(), evento.campanha());
                        }
                    }
                    if (evento.testeAcabaEm() != null) {
                        avisarFimDoTeste(conta, evento.testeAcabaEm());
                    }
                    Household familia = familias.daContaTravada(conta);
                    boolean aplicado = familia.aplicarAssinatura("WEB", evento.premiumAte(), evento.momento(),
                            evento.inicio(), evento.tipo(), conta.getId());
                    if (aplicado) {
                        familia.registrarPrimeiraCobranca(evento.primeiraCobranca());
                    }
                    log.info("Assinatura web: conta={} familia={} plano={} aplicado={}",
                            conta.getId(), familia.getId(), familia.planoVigente(), aplicado);
                }, () -> log.warn("Assinatura web de conta inexistente id={}", evento.usuarioId())));
    }

    /**
     * E-mail tres dias antes do fim do teste (a Stripe avisa nessa hora). Um por teste: a
     * chave e a conta e a data do fim, entao reenvio do aviso nao manda de novo. Vai mesmo
     * com os lembretes desligados: e aviso de cobranca, nao lembrete.
     *
     * <p>Falha na entrega desfaz o webhook inteiro: a Stripe reenvia e a gente tenta de novo.</p>
     */
    private void avisarFimDoTeste(UserAccount conta, Instant fim) {
        if (!email.ligado()) {
            log.warn("Fim do teste sem e-mail configurado: conta={}", conta.getId());
            return;
        }
        if (!testes.primeiraVez("TESTE_ACABANDO:" + conta.getId() + ":" + fim.getEpochSecond())) {
            return;
        }
        String dia = DATA.format(fim);
        boolean entregue = email.enviar(conta.getEmail(), "Seu teste grátis do Piggu Premium termina em " + dia,
                "Olá! Seu teste grátis do Piggu Premium termina em " + dia + ".\n\n"
                        + "Depois disso, o plano que você escolheu é cobrado no cartão cadastrado e o Premium continua.\n"
                        + "Se não quiser continuar, cancele antes dessa data em Plano > Gerenciar assinatura, "
                        + "sem pagar nada.\n\n"
                        + "Se cancelar depois da primeira cobrança, você ainda tem 7 dias para desistir e receber "
                        + "o valor integral.");
        if (!entregue) {
            throw new UpstreamException("O e-mail de fim do teste não saiu. A Stripe vai reenviar o aviso.");
        }
        log.info("Aviso de fim do teste enviado: conta={}", conta.getId());
    }

    /**
     * Direito de arrependimento (CDC art. 49): nos 7 dias seguintes a primeira cobranca,
     * quem assinou cancela e recebe tudo de volta; durante o teste so cancela, sem nada a
     * devolver. O Premium sai na hora aqui; o aviso da Stripe confirma depois.
     *
     * @return se algo foi devolvido; falso quando cancelou ainda no teste
     */
    @Transactional
    public boolean cancelarComReembolso(UUID usuarioId) {
        UserAccount conta = conta(usuarioId);
        Household familia = familias.daContaTravada(conta);
        if (!conta.getId().equals(familia.getSubscriberId())) {
            throw new BusinessException("Só quem assinou pode pedir o reembolso.");
        }
        if (conta.getStripeCustomerId() == null || !"WEB".equals(familia.getPlanSource())) {
            throw new BusinessException("O reembolso pelo app vale para quem assinou pelo site.");
        }
        boolean reembolsado = pagamentos.cancelarComReembolso(conta.getStripeCustomerId(), PRAZO_DE_ARREPENDIMENTO);
        familia.aplicarAssinatura("WEB", null, Instant.now());
        log.info("Arrependimento: conta={} familia={} reembolsado={}", conta.getId(), familia.getId(), reembolsado);
        return reembolsado;
    }

    /**
     * Compra feita no iPhone: o app manda a transacao assinada pela Apple logo depois de comprar
     * ou ao restaurar. So vale para a conta que o app marcou na compra (appAccountToken).
     */
    @Transactional
    public PlanoResponse aplicarCompraDaApple(UUID usuarioId, String transacao) {
        EventoDeAssinatura evento = appStore.lerTransacao(transacao);
        if (!usuarioId.equals(evento.usuarioId())) {
            throw new BusinessException("Esta assinatura da Apple é de outra conta do Piggu.", HttpStatus.CONFLICT, "OUTRA_CONTA");
        }
        aplicarDaLoja("APP_STORE", conta(usuarioId), evento);
        return plano(usuarioId, null);
    }

    /**
     * App Store Server Notification V2 (renovacao, cancelamento, expiracao, reembolso).
     * Idempotente pelo notificationUUID.
     */
    @Transactional
    public void aplicarAvisoDaApple(String signedPayload) {
        appStore.lerAviso(signedPayload).ifPresent(aviso -> {
            int novo = jdbc.update("INSERT INTO billing_events (event_id, provider) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    aviso.id(), "APP_STORE");
            if (novo == 0) {
                log.info("Aviso da App Store repetido ignorado");
                return;
            }
            EventoDeAssinatura evento = aviso.evento();
            if (evento.usuarioId() == null) {
                // Compra sem appAccountToken: nao veio do app com alguem logado.
                log.warn("Aviso da App Store sem conta Piggu id={}", aviso.id());
                return;
            }
            usuarios.findById(evento.usuarioId()).ifPresentOrElse(conta -> aplicarDaLoja("APP_STORE", conta, evento),
                    () -> log.warn("Aviso da App Store de conta inexistente id={}", evento.usuarioId()));
        });
    }

    /**
     * Compra feita no Android: o app manda o purchaseToken logo depois de comprar ou ao restaurar.
     * So vale para a conta que o app marcou na compra (obfuscatedExternalAccountId).
     */
    @Transactional
    public PlanoResponse aplicarCompraDoGoogle(UUID usuarioId, String token) {
        GooglePlayLoja.AssinaturaDoGoogle compra = googlePlay.lerCompra(token);
        if (!usuarioId.equals(compra.evento().usuarioId())) {
            throw new BusinessException("Esta assinatura do Google Play é de outra conta do Piggu.", HttpStatus.CONFLICT, "OUTRA_CONTA");
        }
        aplicarDoGoogle(conta(usuarioId), compra);
        return plano(usuarioId, null);
    }

    /**
     * Aviso em tempo real do Google Play (renovacao, cancelamento, expiracao, revogacao, reembolso).
     * Sem registro em billing_events: o estado vem sempre do Google, reaplicar e inofensivo, e e
     * o aviso repetido que tenta de novo o acknowledge que falhou.
     */
    @Transactional
    public void aplicarAvisoDoGoogle(String autorizacao, String dados) {
        googlePlay.lerAviso(autorizacao, dados).ifPresent(compra -> {
            UUID dono = compra.evento().usuarioId();
            if (dono == null) {
                // Compra sem obfuscatedExternalAccountId: nao veio do app com alguem logado.
                log.warn("Aviso do Google Play sem conta Piggu");
                return;
            }
            usuarios.findById(dono).ifPresentOrElse(conta -> aplicarDoGoogle(conta, compra),
                    () -> log.warn("Aviso do Google Play de conta inexistente id={}", dono));
        });
    }

    /** Na troca de plano o Google cria outro token e encerra o antigo: o fim do antigo nao derruba o novo. */
    private void aplicarDoGoogle(UserAccount conta, GooglePlayLoja.AssinaturaDoGoogle compra) {
        Household familia = familias.daContaTravada(conta);
        boolean tokenAntigo = compra.evento().premiumAte() == null && "PLAY_STORE".equals(familia.getPlanSource())
                && compra.vence() != null && familia.getPremiumUntil() != null
                // Em milissegundos, a precisao do Google: o banco guarda microssegundos arredondados, e
                // o mesmo vencimento pareceria posterior (o fim nunca chegava).
                && familia.getPremiumUntil().truncatedTo(ChronoUnit.MILLIS).isAfter(compra.vence().truncatedTo(ChronoUnit.MILLIS));
        if (tokenAntigo) {
            log.info("Fim de assinatura antiga do Google Play ignorado: conta={} familia={}", conta.getId(), familia.getId());
        } else {
            aplicarDaLoja("PLAY_STORE", conta, compra.evento());
        }
        googlePlay.confirmar(compra);
    }

    /** Premium pago por outro caminho (o site ou a outra loja) segue valendo: a loja nao derruba nem encurta. */
    private void aplicarDaLoja(String origem, UserAccount conta, EventoDeAssinatura evento) {
        if (evento.emTeste()) {
            conta.marcarTesteGratisUsado();
        }
        Household familia = familias.daContaTravada(conta);
        boolean aplicado = familia.aplicarAssinatura(origem, evento.premiumAte(), evento.momento(),
                evento.inicio(), evento.tipo(), conta.getId());
        log.info("Assinatura na loja {}: conta={} familia={} plano={} aplicado={}",
                origem, conta.getId(), familia.getId(), familia.planoVigente(), aplicado);
    }

    private UserAccount conta(UUID usuarioId) {
        return usuarios.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
    }
}
