package com.mecfin.goal.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.goal.domain.Goal;
import com.mecfin.goal.domain.GoalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GoalViewTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    private static Goal goal(String target, LocalDate targetDate) {
        return new Goal(UUID.randomUUID(), "Viagem", new BigDecimal(target), targetDate, null, null);
    }

    @Test
    void monthlyNeededSpreadsTheRemainderUntilTheDeadlineRoundingUp() {
        // Set/2026 até dez/2026 = 4 meses de aporte (set, out, nov, dez); faltam 1000.
        GoalView view = GoalView.of(goal("1500", LocalDate.of(2026, 12, 15)), new BigDecimal("500"), BigDecimal.ZERO, TODAY);

        assertThat(view.status()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(view.remainingAmount()).isEqualByComparingTo("1000");
        assertThat(view.monthsRemaining()).isEqualTo(4);
        assertThat(view.monthlyNeeded()).isEqualByComparingTo("250.00");
        assertThat(view.progressPercent()).isEqualByComparingTo("33.3");
    }

    @Test
    void roundsMonthlyNeededUpSoTheUserNeverFallsShort() {
        GoalView view = GoalView.of(goal("100", LocalDate.of(2026, 11, 1)), BigDecimal.ZERO, BigDecimal.ZERO, TODAY);

        assertThat(view.monthsRemaining()).isEqualTo(3);
        assertThat(view.monthlyNeeded()).isEqualByComparingTo("33.34");
    }

    @Test
    void completedCapsProgressAtHundredAndHasNoMonthlyNeed() {
        GoalView view = GoalView.of(goal("1000", LocalDate.of(2026, 12, 1)), new BigDecimal("1200"), BigDecimal.ZERO, TODAY);

        assertThat(view.status()).isEqualTo(GoalStatus.COMPLETED);
        assertThat(view.progressPercent()).isEqualByComparingTo("100");
        assertThat(view.remainingAmount()).isEqualByComparingTo("0");
        assertThat(view.monthlyNeeded()).isNull();
    }

    @Test
    void pastDeadlineWithoutReachingTargetIsOverdue() {
        GoalView view = GoalView.of(goal("1000", LocalDate.of(2026, 9, 1)), new BigDecimal("10"), BigDecimal.ZERO, TODAY);

        assertThat(view.status()).isEqualTo(GoalStatus.OVERDUE);
        assertThat(view.monthsRemaining()).isNull();
    }

    @Test
    void goalWithoutDeadlineHasNoMonthlyNeed() {
        GoalView view = GoalView.of(goal("1000", null), BigDecimal.ZERO, BigDecimal.ZERO, TODAY);

        assertThat(view.status()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(view.monthlyNeeded()).isNull();
        assertThat(view.progressPercent()).isEqualByComparingTo("0");
    }
}
