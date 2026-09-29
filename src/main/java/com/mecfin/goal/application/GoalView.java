package com.mecfin.goal.application;

import com.mecfin.goal.domain.Goal;
import com.mecfin.goal.domain.GoalStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Meta + tudo que é derivado dos aportes. Calculado no backend (o frontend só desenha):
 * <ul>
 *   <li>progressPercent: limitado a 100 mesmo se o guardado passou do alvo;</li>
 *   <li>monthsRemaining: quantos meses de aporte ainda cabem até o prazo, contando o mês
 *       corrente (prazo este mês = 1); null sem prazo ou com prazo vencido;</li>
 *   <li>monthlyNeeded: quanto aportar por mês para chegar no prazo (arredondado para cima -
 *       arredondar para baixo faria o usuário ficar alguns centavos aquém).</li>
 * </ul>
 */
public record GoalView(
        Goal goal,
        BigDecimal savedAmount,
        BigDecimal savedThisMonth,
        BigDecimal remainingAmount,
        BigDecimal progressPercent,
        GoalStatus status,
        Integer monthsRemaining,
        BigDecimal monthlyNeeded) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static GoalView of(Goal goal, BigDecimal saved, BigDecimal savedThisMonth, LocalDate today) {
        BigDecimal target = goal.getTargetAmount();
        BigDecimal remaining = target.subtract(saved).max(BigDecimal.ZERO);
        BigDecimal progress = saved.signum() <= 0
                ? BigDecimal.ZERO
                : saved.multiply(HUNDRED).divide(target, 1, RoundingMode.DOWN).min(HUNDRED);
        GoalStatus status = status(goal, saved, today);

        Integer monthsRemaining = null;
        BigDecimal monthlyNeeded = null;
        if (status == GoalStatus.ACTIVE && goal.getTargetDate() != null) {
            monthsRemaining = (int) YearMonth.from(today).until(YearMonth.from(goal.getTargetDate()), ChronoUnit.MONTHS) + 1;
            monthlyNeeded = remaining.divide(BigDecimal.valueOf(monthsRemaining), 2, RoundingMode.CEILING);
        }
        return new GoalView(goal, saved, savedThisMonth, remaining, progress, status, monthsRemaining, monthlyNeeded);
    }

    private static GoalStatus status(Goal goal, BigDecimal saved, LocalDate today) {
        if (goal.isArchived()) {
            return GoalStatus.ARCHIVED;
        }
        if (saved.compareTo(goal.getTargetAmount()) >= 0) {
            return GoalStatus.COMPLETED;
        }
        if (goal.getTargetDate() != null && goal.getTargetDate().isBefore(today)) {
            return GoalStatus.OVERDUE;
        }
        return GoalStatus.ACTIVE;
    }
}
