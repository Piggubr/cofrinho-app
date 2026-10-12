package com.piggu.lifestyle.api.dto;

import com.piggu.lifestyle.domain.PersonalGoal;

import java.util.UUID;

/**
 * @param mes       mes da meta, no formato AAAA-MM
 * @param concluida verdadeiro quando o atual chegou ao alvo
 */
public record PersonalGoalResponse(
        UUID id,
        String titulo,
        int alvo,
        int atual,
        String mes,
        boolean concluida
) {

    public static PersonalGoalResponse de(PersonalGoal meta) {
        return new PersonalGoalResponse(
                meta.getId(),
                meta.getTitle(),
                meta.getTarget(),
                meta.getCurrent(),
                meta.getMes().toString(),
                meta.isConcluida()
        );
    }
}
