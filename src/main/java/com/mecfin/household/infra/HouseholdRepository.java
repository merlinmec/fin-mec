package com.mecfin.household.infra;

import com.mecfin.household.domain.Household;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdRepository extends JpaRepository<Household, UUID> {
}
