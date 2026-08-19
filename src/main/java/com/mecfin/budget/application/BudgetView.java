package com.mecfin.budget.application;

import com.mecfin.budget.domain.Budget;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Budget + gasto realizado calculado na leitura (nunca persistido, ver
 * {@link Budget}). Modelo de leitura composto, não entidade JPA.
 */
public record BudgetView(Budget budget, BigDecimal spent) {

    public BigDecimal percentageUsed() {
        // Money usa HALF_EVEN em todo o projeto (ver decisão de arquitetura em
        // generic-rolling-lemur.md seção C); amount de Budget é sempre positivo (@Positive
        // na API), então não há divisão por zero aqui.
        return spent.divide(budget.getAmount(), 4, RoundingMode.HALF_EVEN).multiply(BigDecimal.valueOf(100));
    }
}
