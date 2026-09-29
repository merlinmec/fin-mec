package com.mecfin.household.infra;

import com.mecfin.household.domain.HouseholdMember;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, UUID> {

    /** Membro com o e-mail da conta, para a tela de compartilhamento. */
    interface MemberRow {
        UUID getUserId();

        String getEmail();

        String getRole();

        Instant getJoinedAt();
    }

    // Usado no login (UserDetailsServiceImpl) para resolver o household do usuário
    // autenticado sem carregar a entidade HouseholdMember inteira.
    @Query("select hm.householdId from HouseholdMember hm where hm.userId = :userId")
    Optional<UUID> findHouseholdIdByUserId(@Param("userId") UUID userId);

    Optional<HouseholdMember> findByUserId(UUID userId);

    Optional<HouseholdMember> findByHouseholdIdAndUserId(UUID householdId, UUID userId);

    List<HouseholdMember> findAllByHouseholdIdOrderByJoinedAtAsc(UUID householdId);

    long countByHouseholdId(UUID householdId);

    @Query(value = """
            SELECT hm.user_id AS userId, u.email AS email, hm.role AS role, hm.joined_at AS joinedAt
            FROM household_members hm JOIN users u ON u.id = hm.user_id
            WHERE hm.household_id = :householdId
            ORDER BY hm.joined_at
            """, nativeQuery = true)
    List<MemberRow> findMemberRows(@Param("householdId") UUID householdId);

    // O household não tem entidade de usuário (fica na identidade); o e-mail é só leitura aqui.
    @Query(value = "SELECT email FROM users WHERE id = :userId", nativeQuery = true)
    Optional<String> findEmailByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from HouseholdMember hm where hm.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
