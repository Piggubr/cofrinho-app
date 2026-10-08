package com.piggu.identity.billing;

import com.piggu.identity.domain.UserAccount;

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

    /** Endereco da pagina de pagamento do provedor. */
    String abrirCheckout(UserAccount conta, Periodo periodo);

    /** Endereco onde a pessoa troca cartao, ve recibos e cancela. */
    String abrirPortal(UserAccount conta);

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
