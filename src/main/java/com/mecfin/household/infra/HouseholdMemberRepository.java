package com.mecfin.household.infra;

import com.mecfin.household.domain.HouseholdMember;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, UUID> {

    // Usado no login (UserDetailsServiceImpl) para resolver o household do usuário
    // autenticado sem carregar a entidade HouseholdMember inteira.
    @Query("select hm.householdId from HouseholdMember hm where hm.userId = :userId")
    Optional<UUID> findHouseholdIdByUserId(@Param("userId") UUID userId);
}
