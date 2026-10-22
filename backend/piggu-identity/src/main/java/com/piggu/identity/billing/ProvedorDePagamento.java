package com.piggu.identity.billing;

import com.piggu.identity.domain.UserAccount;

import java.time.Duration;
import java.util.Optional;

/**
 * Cobranca do Premium no site. Hoje a Stripe; trocar de provedor e escrever outra
 * implementacao, sem mexer em plano, token nem telas.
 *
 * <p>Nenhum dado de cartao passa pelo Piggu: a pessoa paga numa pagina do provedor
 * e o Piggu so fica sabendo pelo webhook.</p>
 */
public interface ProvedorDePagamento {

    boolean habilitado();

    /** O plano familia tem preco configurado no provedor. */
    boolean familiaHabilitado();

    /** O plano casal tem preco configurado no provedor. */
    boolean casalHabilitado();

    /**
     * Endereco da pagina de pagamento do provedor.
     *
     * @param diasDeTeste dias gratis antes da primeira cobranca; 0 cobra na hora
     * @param campanha    campanha de teste gratis, devolvida no webhook para registrar o uso; pode ser nula
     */
    String abrirCheckout(UserAccount conta, Periodo periodo, int diasDeTeste, String campanha);

    default String abrirCheckout(UserAccount conta, Periodo periodo, int diasDeTeste) {
        return abrirCheckout(conta, periodo, diasDeTeste, null);
    }

    /** Endereco onde a pessoa troca cartao, ve recibos e cancela. */
    String abrirPortal(UserAccount conta);

    /**
     * Arrependimento (CDC art. 49): cancela na hora e devolve tudo o que foi cobrado
     * nesta assinatura, se a primeira cobranca paga tem menos de {@code prazo}. Ainda no
     * teste (nada cobrado), so cancela.
     *
     * @return se alguma cobranca foi devolvida; falso quando cancelou no teste
     * @throws com.piggu.common.error.BusinessException quando passou do prazo ou nao ha assinatura
     */
    boolean cancelarComReembolso(String clienteNoProvedor, Duration prazo);

    /**
     * Apaga o cliente no provedor, o que cancela na hora qualquer assinatura dele.
     * Usado quando a conta e excluida: ninguem continua pagando por uma conta que nao existe.
     */
    void encerrarCliente(String clienteNoProvedor);

    /**
     * Confere a assinatura do aviso e o traduz.
     *
     * @return vazio quando o aviso e valido mas nao muda a assinatura de ninguem
     * @throws com.piggu.common.error.UnauthorizedException quando a assinatura nao confere
     */
    Optional<EventoDeAssinatura> lerWebhook(String corpo, String assinatura);
}
