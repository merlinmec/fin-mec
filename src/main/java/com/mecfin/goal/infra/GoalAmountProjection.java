package com.mecfin.goal.infra;

import java.math.BigDecimal;
import java.util.UUID;

public interface GoalAmountProjection {

    UUID getGoalId();

    BigDecimal getTotal();
}
