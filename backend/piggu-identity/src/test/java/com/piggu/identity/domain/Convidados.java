package com.piggu.identity.domain;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.security.CurrentUser;

/** Conta nova que aceita o convite pendente: e assim que um convidado entra na familia. */
public final class Convidados {

    private Convidados() {
    }

    public static UserAccount entrar(FamiliaService familias, HouseholdInviteRepository convites,
                                     UserAccountRepository usuarios, String email, String nome) {
        UserAccount nova = familias.criarConta(email, nome, Consentimentos.VERSAO_DO_AVISO);
        familias.aceitarConvite(new CurrentUser(nova.getId(), nova.getEmail(), nova.getRole(), null, nova.getHouseholdId()),
                convites.findByEmailOrderByCreatedAtDesc(email).getFirst().getId());
        return usuarios.findById(nova.getId()).orElseThrow();
    }
}
