package com.piggu.identity.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    /**
     * Para o login por senha: a linha fica presa ate o fim da transacao, entao tentativas ao
     * mesmo tempo na mesma conta contam uma a uma (sem isso uma rajada perdia os erros e a trava).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserAccount u WHERE u.email = :email")
    Optional<UserAccount> travarParaLogin(@Param("email") String email);

    Optional<UserAccount> findByEmailTokenHash(String emailTokenHash);

    Optional<UserAccount> findByPasswordResetHash(String passwordResetHash);

    List<UserAccount> findAllByOrderByEmailAsc();

    List<UserAccount> findByCpfIndice(String cpfIndice);

    List<UserAccount> findByHouseholdIdOrderByCreatedAtAsc(UUID householdId);

    /**
     * Sem nenhum acesso desde o limite (conta que nunca entrou conta pela criacao). Quem paga um
     * Premium que vale fica: apagar derrubaria o plano da casa e a loja seguiria cobrando (D-144).
     */
    @Query("""
            SELECT u.id FROM UserAccount u WHERE COALESCE(u.lastLoginAt, u.createdAt) < :limite
              AND NOT EXISTS (SELECT 1 FROM Household h WHERE h.subscriberId = u.id AND h.premiumUntil > CURRENT_TIMESTAMP)
            """)
    List<UUID> semAcessoDesde(@Param("limite") Instant limite);

    /** Quem pediu aviso fora do app e ve as financas da familia (o membro nao ve). */
    @Query("SELECT u FROM UserAccount u WHERE u.active = true AND (u.alertsByEmail = true OR u.alertsByPush = true)"
            + " AND u.role <> com.piggu.common.security.PigguRole.MEMBRO ORDER BY u.createdAt")
    List<UserAccount> comAvisosLigados();

    @Query("SELECT u.id FROM UserAccount u WHERE u.active = false AND u.deactivatedAt < :limite")
    List<UUID> desativadasDesde(@Param("limite") Instant limite);
}
