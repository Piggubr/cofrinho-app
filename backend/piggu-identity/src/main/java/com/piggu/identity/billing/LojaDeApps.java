package com.piggu.identity.billing;

import java.util.Optional;

/**
 * Assinatura comprada dentro do app (App Store / Google Play), que as lojas exigem para
 * conteudo digital vendido no app.
 *
 * <p>Quem valida o recibo com a Apple e o Google e o intermediario (hoje o RevenueCat);
 * o Piggu so recebe o aviso ja validado. Trocar de intermediario e escrever outra
 * implementacao, sem mexer em plano, token nem telas.</p>
 */
public interface LojaDeApps {

    boolean habilitada();

    /**
     * Confere a autenticacao do aviso e o traduz.
     *
     * @param loja        a loja do caminho: APP_STORE ou PLAY_STORE
     * @param autorizacao o cabecalho Authorization que o intermediario mandou
     * @return vazio quando o aviso e valido mas nao muda a assinatura de ninguem
     * @throws com.piggu.common.error.UnauthorizedException quando a autenticacao nao confere
     */
    Optional<AvisoDaLoja> lerAviso(String loja, String corpo, String autorizacao);

    /**
     * @param id     identificador unico do aviso, para processar cada um uma vez so
     * @param origem APP_STORE ou PLAY_STORE
     */
    record AvisoDaLoja(String id, String origem, EventoDeAssinatura evento) {
    }
}
