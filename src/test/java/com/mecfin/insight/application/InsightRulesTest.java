package com.mecfin.insight.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.insight.application.BalanceForecast.Event;
import com.mecfin.insight.application.BalanceForecast.EventKind;
import com.mecfin.report.application.CashFlowMonth;
import com.mecfin.report.application.CategoryReportLine;
import com.mecfin.report.application.MonthAmount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regras puras da Fase 19: curva de saldo, frases do resumo e limiares dos alertas. */
class InsightRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);
    private static final YearMonth SEPT = YearMonth.of(2026, 9);

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void forecastKeepsTheLowestPointEvenWhenTheBalanceRecovers() {
        BalanceForecast f = ForecastService.build(TODAY, TODAY.plusDays(10), bd("100"), List.of(
                new Event(TODAY.plusDays(2), EventKind.BILL, "Aluguel", bd("-300"), false),
                new Event(TODAY.plusDays(5), EventKind.TRANSACTION, "Salário", bd("1000"), false)));

        assertThat(f.points()).hasSize(11);
        assertThat(f.lowestBalance()).isEqualByComparingTo("-200");
        assertThat(f.lowestDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(f.firstNegativeDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(f.endBalance()).isEqualByComparingTo("800");
        assertThat(f.totalIncome()).isEqualByComparingTo("1000");
        assertThat(f.totalExpense()).isEqualByComparingTo("300");
    }

    @Test
    void forecastAlreadyNegativeTodayStartsNegative() {
        BalanceForecast f = ForecastService.build(TODAY, TODAY.plusDays(7), bd("-10"), List.of());
        assertThat(f.firstNegativeDate()).isEqualTo(TODAY);
    }

    private static CashFlowMonth month(YearMonth m, String income, String expense) {
        return new CashFlowMonth(m, bd(income), bd(expense), bd(income).subtract(bd(expense)), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void summarySaysWhenYouSpentMoreThanYouEarned() {
        MonthlySummary s = MonthlySummaryService.compose(SEPT, month(SEPT.minusMonths(1), "3000", "3000"),
                month(SEPT, "3000", "3450"), List.of());
        assertThat(s.savingsRate()).isEqualByComparingTo("-15.0");
        assertThat(s.headlines()).containsExactly(
                "Em setembro você gastou R$ 450,00 a mais do que ganhou.",
                "Os gastos ficaram 15% acima de agosto.");
    }

    @Test
    void summaryWithoutIncomeOrDataDoesNotInventRates() {
        MonthlySummary noIncome = MonthlySummaryService.compose(SEPT, month(SEPT.minusMonths(1), "0", "0"),
                month(SEPT, "0", "200"), List.of());
        assertThat(noIncome.savingsRate()).isNull();
        assertThat(noIncome.expenseChangePercent()).isNull();
        assertThat(noIncome.headlines()).containsExactly(
                "Nenhuma receita registrada em setembro; os gastos somaram R$ 200,00.");

        MonthlySummary empty = MonthlySummaryService.compose(SEPT, month(SEPT.minusMonths(1), "0", "0"),
                month(SEPT, "0", "0"), List.of());
        assertThat(empty.hasData()).isFalse();
        assertThat(empty.headlines()).isEmpty();
    }

    private static CategoryReportLine line(String name, String total, String previous, String change) {
        return new CategoryReportLine(UUID.randomUUID(), name, null, null, bd(total), bd("50"), bd(previous),
                change == null ? null : bd(change), bd(total), List.of());
    }

    @Test
    void biggestIncreaseDropsTheUnreadablePercentageOnATinyBase() {
        MonthlySummary tiny = MonthlySummaryService.compose(SEPT, month(SEPT.minusMonths(1), "5000", "4000"),
                month(SEPT, "5000", "4000"), List.of(line("Lazer", "1945.90", "55.90", "3381.1")));
        assertThat(tiny.headlines()).last().isEqualTo("Lazer subiu R$ 1.890,00 em relação a agosto.");

        MonthlySummary normal = MonthlySummaryService.compose(SEPT, month(SEPT.minusMonths(1), "5000", "4000"),
                month(SEPT, "5000", "4000"), List.of(line("Mercado", "1200", "800", "50.0")));
        assertThat(normal.headlines()).last().isEqualTo("Mercado subiu 50% em relação a agosto (+R$ 400,00).");
    }

    private static CategoryReportLine history(String... monthly) {
        List<MonthAmount> amounts = List.of(
                new MonthAmount(SEPT.minusMonths(3), bd(monthly[0])),
                new MonthAmount(SEPT.minusMonths(2), bd(monthly[1])),
                new MonthAmount(SEPT.minusMonths(1), bd(monthly[2])));
        BigDecimal total = amounts.stream().map(MonthAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CategoryReportLine(UUID.randomUUID(), "Restaurantes", null, null, total, null, null, null,
                total.divide(bd("3"), 2, java.math.RoundingMode.HALF_EVEN), amounts);
    }

    @Test
    void spikeNeedsAHabitABigEnoughAverageAndFiftyPercentMore() {
        assertThat(InsightAlertSource.spike(SEPT, history("200", "200", "200"), bd("299.99"))).isNull();
        assertThat(InsightAlertSource.spike(SEPT, history("200", "200", "200"), bd("300"))).isNotNull();
        // gasto num mês só não é hábito
        assertThat(InsightAlertSource.spike(SEPT, history("0", "0", "900"), bd("2000"))).isNull();
        // média pequena: R$ 40 → R$ 80 é +100%, mas não é alarme
        assertThat(InsightAlertSource.spike(SEPT, history("40", "40", "40"), bd("80"))).isNull();
        assertThat(InsightAlertSource.spike(SEPT, history("200", "200", "200"), null)).isNull();
    }
}
