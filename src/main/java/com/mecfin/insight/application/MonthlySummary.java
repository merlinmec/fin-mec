package com.mecfin.insight.application;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Resumo de um mês fechado (Fase 19): números + frases prontas ("você guardou 18% da renda em
 * setembro"). As frases saem do backend para a regra (o que vale a pena dizer, com que
 * limiares) ficar num lugar só e testada.
 *
 * <p>savingsRate e expenseChangePercent são nulos quando não há base (sem receita / mês anterior
 * sem gasto).
 */
public record MonthlySummary(
        YearMonth month,
        boolean hasData,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net,
        BigDecimal savingsRate,
        BigDecimal previousExpense,
        BigDecimal expenseChangePercent,
        List<CategoryHighlight> topCategories,
        CategoryHighlight biggestIncrease,
        List<String> headlines) {

    public record CategoryHighlight(UUID categoryId, String name, String color, String icon, BigDecimal total,
            BigDecimal share, BigDecimal previousTotal, BigDecimal changePercent) {
    }
}
