package com.mecfin.budget.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

// category e referenceMonth nao aparecem aqui de proposito: sao imutaveis apos a criacao
// (ver Budget.updateAmount) - so o valor planejado e editavel.
public record UpdateBudgetRequest(@NotNull @Positive BigDecimal amount) {
}
