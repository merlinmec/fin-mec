package com.mecfin.goal.application;

import com.mecfin.goal.domain.Goal;
import com.mecfin.goal.domain.GoalContribution;
import com.mecfin.goal.infra.GoalAmountProjection;
import com.mecfin.goal.infra.GoalContributionRepository;
import com.mecfin.goal.infra.GoalRepository;
import com.mecfin.shared.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalContributionRepository contributionRepository;
    private final Clock clock;

    public GoalService(GoalRepository goalRepository, GoalContributionRepository contributionRepository, Clock clock) {
        this.goalRepository = goalRepository;
        this.contributionRepository = contributionRepository;
        this.clock = clock;
    }

    @Transactional
    public GoalView create(String name, BigDecimal targetAmount, LocalDate targetDate, String color, String icon) {
        Goal goal = goalRepository.save(new Goal(CurrentUser.householdId(), name, targetAmount, targetDate, color, icon));
        return GoalView.of(goal, BigDecimal.ZERO, BigDecimal.ZERO, today());
    }

    @Transactional(readOnly = true)
    public List<GoalView> list(boolean includeArchived) {
        List<Goal> goals = goalRepository.findAllByHouseholdIdOrderByCreatedAtAsc(CurrentUser.householdId()).stream()
                .filter(goal -> includeArchived || !goal.isArchived())
                .toList();
        if (goals.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = goals.stream().map(Goal::getId).toList();
        Map<UUID, BigDecimal> saved = toMap(contributionRepository.sumByGoal(ids));
        Map<UUID, BigDecimal> thisMonth = toMap(contributionRepository.sumByGoalSince(ids, today().withDayOfMonth(1)));
        return goals.stream()
                .map(goal -> GoalView.of(goal, saved.getOrDefault(goal.getId(), BigDecimal.ZERO),
                        thisMonth.getOrDefault(goal.getId(), BigDecimal.ZERO), today()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GoalView get(UUID id) {
        return view(getOwnedOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<GoalContribution> contributions(UUID goalId) {
        return contributionRepository.findAllByGoalIdOrderByContributionDateDescCreatedAtDesc(getOwnedOrThrow(goalId).getId());
    }

    @Transactional
    public GoalView update(UUID id, String name, BigDecimal targetAmount, LocalDate targetDate, String color,
            String icon, boolean archived) {
        Goal goal = getOwnedOrThrow(id);
        goal.update(name, targetAmount, targetDate, color, icon, archived);
        return view(goal);
    }

    // Hard delete: os aportes vão junto (ON DELETE CASCADE). Meta não movimenta conta, então não
    // há saldo nem histórico financeiro a preservar - diferente de lançamento.
    @Transactional
    public void delete(UUID id) {
        goalRepository.delete(getOwnedOrThrow(id));
    }

    /** Aporte (amount > 0) ou resgate (amount < 0). O guardado nunca pode ficar negativo. */
    @Transactional
    public GoalView contribute(UUID goalId, BigDecimal amount, LocalDate date, String note) {
        Goal goal = getOwnedOrThrow(goalId);
        if (goal.isArchived()) {
            throw new IllegalArgumentException("Meta arquivada não recebe aportes - desarquive primeiro");
        }
        BigDecimal saved = contributionRepository.sumByGoalId(goal.getId());
        if (saved.add(amount).signum() < 0) {
            throw new IllegalArgumentException("Resgate maior que o valor guardado na meta (" + saved.toPlainString() + ")");
        }
        contributionRepository.save(new GoalContribution(goal.getId(), amount, date != null ? date : today(),
                note == null || note.isBlank() ? null : note.strip()));
        return view(goal);
    }

    @Transactional
    public GoalView removeContribution(UUID goalId, UUID contributionId) {
        Goal goal = getOwnedOrThrow(goalId);
        GoalContribution contribution = contributionRepository.findByIdAndGoalId(contributionId, goal.getId())
                .orElseThrow(() -> new GoalContributionNotFoundException(contributionId));
        BigDecimal saved = contributionRepository.sumByGoalId(goal.getId());
        if (saved.subtract(contribution.getAmount()).signum() < 0) {
            throw new IllegalArgumentException(
                    "Excluir este aporte deixaria a meta com saldo negativo - exclua antes o resgate correspondente");
        }
        contributionRepository.delete(contribution);
        return view(goal);
    }

    private GoalView view(Goal goal) {
        List<UUID> ids = List.of(goal.getId());
        BigDecimal saved = contributionRepository.sumByGoalId(goal.getId());
        BigDecimal thisMonth = toMap(contributionRepository.sumByGoalSince(ids, today().withDayOfMonth(1)))
                .getOrDefault(goal.getId(), BigDecimal.ZERO);
        return GoalView.of(goal, saved, thisMonth, today());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static Map<UUID, BigDecimal> toMap(List<GoalAmountProjection> rows) {
        return rows.stream().collect(Collectors.toMap(GoalAmountProjection::getGoalId, GoalAmountProjection::getTotal));
    }

    private Goal getOwnedOrThrow(UUID id) {
        return goalRepository.findByIdAndHouseholdId(id, CurrentUser.householdId())
                .orElseThrow(() -> new GoalNotFoundException(id));
    }
}
