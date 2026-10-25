package com.mecfin.goal.api;

import com.mecfin.goal.domain.GoalContribution;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// transfer = o aporte/resgate moveu dinheiro entre contas (excluí-lo cancela a transferência).
public record GoalContributionResponse(
        UUID id, BigDecimal amount, LocalDate date, String note, boolean transfer, Instant createdAt) {

    public static GoalContributionResponse from(GoalContribution contribution) {
        return new GoalContributionResponse(contribution.getId(), contribution.getAmount(),
                contribution.getContributionDate(), contribution.getNote(),
                contribution.getTransferTransactionId() != null, contribution.getCreatedAt());
    }
}
