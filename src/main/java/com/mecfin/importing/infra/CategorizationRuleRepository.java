package com.mecfin.importing.infra;

import com.mecfin.importing.domain.CategorizationRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategorizationRuleRepository extends JpaRepository<CategorizationRule, UUID> {

    List<CategorizationRule> findAllByHouseholdIdOrderByPatternAsc(UUID householdId);

    Optional<CategorizationRule> findByIdAndHouseholdId(UUID id, UUID householdId);

    boolean existsByHouseholdIdAndPatternAndIdNot(UUID householdId, String pattern, UUID id);

    boolean existsByHouseholdIdAndPattern(UUID householdId, String pattern);
}
