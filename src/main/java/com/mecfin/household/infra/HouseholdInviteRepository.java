package com.mecfin.household.infra;

import com.mecfin.household.domain.HouseholdInvite;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdInviteRepository extends JpaRepository<HouseholdInvite, UUID> {

    Optional<HouseholdInvite> findByTokenHash(String tokenHash);

    Optional<HouseholdInvite> findByIdAndHouseholdId(UUID id, UUID householdId);

    List<HouseholdInvite> findAllByHouseholdIdOrderByCreatedAtDesc(UUID householdId);
}
