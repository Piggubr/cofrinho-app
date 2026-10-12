package com.piggu.identity.api.dto;

import com.piggu.common.security.PigguRole;
import com.piggu.common.security.Plano;
import com.piggu.identity.domain.Household;
import com.piggu.identity.domain.HouseholdInvite;
import com.piggu.identity.domain.UserAccount;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A familia de quem pergunta: nome, plano, quem faz parte e os convites pendentes
 * (estes so para o titular).
 *
 * @param plano        plano da casa: PREMIUM quando ha assinatura vigente, mesmo individual
 * @param planoFamilia a assinatura vigente e o plano familia (vale para todos)
 */
public record FamiliaResponse(UUID id, String nome, Plano plano, boolean planoFamilia, List<Membro> membros,
                              List<Convite> convites) {

    /** @param premium esta pessoa tem Premium (no individual, so quem assinou) */
    public record Membro(UUID id, String nome, String email, String foto, PigguRole papel, boolean premium) {
    }

    /** @param familia nome da familia que convidou, para quem recebe o convite */
    public record Convite(UUID id, String email, Instant venceEm, String familia) {
    }

    public static FamiliaResponse de(Household familia, List<UserAccount> membros, List<HouseholdInvite> convites) {
        return new FamiliaResponse(
                familia.getId(),
                familia.getName(),
                familia.planoVigente(),
                familia.planoVigente() == Plano.PREMIUM && familia.isFamilia(),
                membros.stream()
                        .map(conta -> new Membro(conta.getId(), conta.nomeExibicao(), conta.getEmail(),
                                conta.getGooglePicture(), conta.getRole(),
                                familia.planoPara(conta.getId()) == Plano.PREMIUM))
                        .toList(),
                convites.stream()
                        .map(convite -> new Convite(convite.getId(), convite.getEmail(), convite.getExpiresAt(),
                                familia.getName()))
                        .toList());
    }
}
