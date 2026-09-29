package com.mecfin.goal.infra;

import com.mecfin.goal.domain.GoalContribution;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, UUID> {

    List<GoalContribution> findAllByGoalIdOrderByContributionDateDescCreatedAtDesc(UUID goalId);

    Optional<GoalContribution> findByIdAndGoalId(UUID id, UUID goalId);

    @Query("SELECT c.goalId AS goalId, COALESCE(SUM(c.amount), 0) AS total FROM GoalContribution c "
            + "WHERE c.goalId IN :goalIds GROUP BY c.goalId")
    List<GoalAmountProjection> sumByGoal(@Param("goalIds") Collection<UUID> goalIds);

    // Aportado a partir de uma data (inclusive) - "quanto guardei este mês" no card da meta.
    @Query("SELECT c.goalId AS goalId, COALESCE(SUM(c.amount), 0) AS total FROM GoalContribution c "
            + "WHERE c.goalId IN :goalIds AND c.contributionDate >= :since GROUP BY c.goalId")
    List<GoalAmountProjection> sumByGoalSince(
            @Param("goalIds") Collection<UUID> goalIds, @Param("since") LocalDate since);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM GoalContribution c WHERE c.goalId = :goalId")
    BigDecimal sumByGoalId(@Param("goalId") UUID goalId);
}
