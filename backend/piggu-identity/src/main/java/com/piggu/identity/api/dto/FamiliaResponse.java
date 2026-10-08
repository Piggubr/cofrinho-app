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
 */
public record FamiliaResponse(UUID id, String nome, Plano plano, List<Membro> membros, List<Convite> convites) {

    public record Membro(UUID id, String nome, String email, String foto, PigguRole papel) {
    }

    /** @param familia nome da familia que convidou, para quem recebe o convite */
    public record Convite(UUID id, String email, Instant venceEm, String familia) {
    }

    public static FamiliaResponse de(Household familia, List<UserAccount> membros, List<HouseholdInvite> convites) {
        return new FamiliaResponse(
                familia.getId(),
                familia.getName(),
                familia.planoVigente(),
                membros.stream()
                        .map(conta -> new Membro(conta.getId(), conta.nomeExibicao(), conta.getEmail(),
                                conta.getGooglePicture(), conta.getRole()))
                        .toList(),
                convites.stream()
                        .map(convite -> new Convite(convite.getId(), convite.getEmail(), convite.getExpiresAt(),
                                familia.getName()))
                        .toList());
    }
}
