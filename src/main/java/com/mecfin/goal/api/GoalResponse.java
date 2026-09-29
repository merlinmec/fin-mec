package com.mecfin.goal.api;

import com.mecfin.goal.application.GoalView;
import com.mecfin.goal.domain.GoalStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record GoalResponse(
        UUID id,
        String name,
        BigDecimal targetAmount,
        LocalDate targetDate,
        String color,
        String icon,
        boolean archived,
        BigDecimal savedAmount,
        BigDecimal savedThisMonth,
        BigDecimal remainingAmount,
        BigDecimal progressPercent,
        GoalStatus status,
        Integer monthsRemaining,
        BigDecimal monthlyNeeded,
        Instant createdAt) {

    public static GoalResponse from(GoalView view) {
        return new GoalResponse(
                view.goal().getId(),
                view.goal().getName(),
                view.goal().getTargetAmount(),
                view.goal().getTargetDate(),
                view.goal().getColor(),
                view.goal().getIcon(),
                view.goal().isArchived(),
                view.savedAmount(),
                view.savedThisMonth(),
                view.remainingAmount(),
                view.progressPercent(),
                view.status(),
                view.monthsRemaining(),
                view.monthlyNeeded(),
                view.goal().getCreatedAt());
    }
}
