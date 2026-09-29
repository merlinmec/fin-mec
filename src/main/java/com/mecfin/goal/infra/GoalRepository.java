package com.mecfin.goal.infra;

import com.mecfin.goal.domain.Goal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findAllByHouseholdIdOrderByCreatedAtAsc(UUID householdId);

    Optional<Goal> findByIdAndHouseholdId(UUID id, UUID householdId);
}
