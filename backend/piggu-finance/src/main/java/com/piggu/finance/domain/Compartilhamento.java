package com.piggu.finance.domain;

import com.piggu.common.error.ForbiddenException;
import com.piggu.common.security.CurrentUser;

import java.util.UUID;

/**
 * Regras do que foi compartilhado (receita, conta fixa, conta ou cartao, cofrinho): quem
 * ve pelo compartilhamento tambem edita, mas so quem criou apaga ou volta para "so meu".
 * O que nao e compartilhado so quem criou (e quem ve tudo dele) mexe, como antes.
 */
final class Compartilhamento {

    private Compartilhamento() {
    }

    static void exigirEdicao(CurrentUser usuario, UUID dono, boolean compartilhado, String oQue) {
        if (!compartilhado && !usuario.podeGerenciar(dono)) {
            throw new ForbiddenException("Só quem criou " + oQue + " pode mudar.");
        }
    }

    static void exigirDono(CurrentUser usuario, UUID dono, String oQue) {
        if (!usuario.podeGerenciar(dono)) {
            throw new ForbiddenException("Só quem criou " + oQue + " pode apagar.");
        }
    }

    /**
     * @param pedido nulo mantem como esta
     * @return se o pedido troca o compartilhamento (so quem criou pode)
     */
    static boolean muda(CurrentUser usuario, UUID dono, boolean atual, Boolean pedido, String oQue) {
        if (pedido == null || pedido == atual) {
            return false;
        }
        if (!usuario.podeGerenciar(dono)) {
            throw new ForbiddenException("Só quem criou " + oQue + " decide se é compartilhado.");
        }
        return true;
    }
}
