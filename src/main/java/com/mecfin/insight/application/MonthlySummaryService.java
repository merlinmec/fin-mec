package com.mecfin.insight.application;

import static com.mecfin.insight.application.InsightText.money;
import static com.mecfin.insight.application.InsightText.monthName;
import static com.mecfin.insight.application.InsightText.percent;

import com.mecfin.insight.application.MonthlySummary.CategoryHighlight;
import com.mecfin.report.application.CashFlowMonth;
import com.mecfin.report.application.CategoryReport;
import com.mecfin.report.application.CategoryReportLine;
import com.mecfin.report.application.ReportService;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resumo mensal automático (Fase 19), montado em cima dos relatórios da Fase 12. */
@Service
public class MonthlySummaryService {

    // Aumento de categoria só vira destaque acima disto: R$ 12 a mais em "Padaria" não é notícia.
    static final BigDecimal MIN_INCREASE = new BigDecimal("50");
    static final BigDecimal MAX_READABLE_PERCENT = new BigDecimal("200");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ReportService reportService;
    private final Clock clock;

    public MonthlySummaryService(ReportService reportService, Clock clock) {
        this.reportService = reportService;
        this.clock = clock;
    }

    /** Sem mês: o último mês fechado (o resumo do mês corrente estaria sempre pela metade). */
    @Transactional(readOnly = true)
    public MonthlySummary summarize(YearMonth month) {
        YearMonth target = month != null ? month : YearMonth.now(clock).minusMonths(1);
        List<CashFlowMonth> months = reportService.cashFlow(target.minusMonths(1), target).months();
        CategoryReport categories = reportService.byCategory(target, target, TransactionType.EXPENSE);
        return compose(target, months.get(0), months.get(1), categories.categories());
    }

    static MonthlySummary compose(YearMonth month, CashFlowMonth previous, CashFlowMonth current,
            List<CategoryReportLine> lines) {
        BigDecimal income = current.income();
        BigDecimal expense = current.expense();
        BigDecimal net = income.subtract(expense);
        BigDecimal savingsRate = income.signum() > 0
                ? net.multiply(HUNDRED).divide(income, 1, RoundingMode.HALF_EVEN)
                : null;
        BigDecimal previousExpense = previous.expense();
        BigDecimal expenseChange = previousExpense.signum() > 0
                ? expense.subtract(previousExpense).multiply(HUNDRED).divide(previousExpense, 1, RoundingMode.HALF_EVEN)
                : null;

        List<CategoryHighlight> top = lines.stream().limit(3).map(MonthlySummaryService::highlight).toList();
        CategoryHighlight biggestIncrease = lines.stream()
                .filter(l -> l.previousTotal().signum() > 0)
                .filter(l -> l.total().subtract(l.previousTotal()).compareTo(MIN_INCREASE) >= 0)
                .max(Comparator.comparing(l -> l.total().subtract(l.previousTotal())))
                .map(MonthlySummaryService::highlight)
                .orElse(null);

        boolean hasData = income.signum() > 0 || expense.signum() > 0;
        List<String> headlines = new ArrayList<>();
        String name = monthName(month);
        String previousName = monthName(month.minusMonths(1));
        if (hasData) {
            if (savingsRate != null && net.signum() >= 0) {
                headlines.add("Você guardou " + percent(savingsRate) + " da renda em " + name + " (" + money(net)
                        + ").");
            } else if (savingsRate != null) {
                headlines.add("Em " + name + " você gastou " + money(net.negate()) + " a mais do que ganhou.");
            } else {
                headlines.add("Nenhuma receita registrada em " + name + "; os gastos somaram " + money(expense) + ".");
            }
            if (expenseChange != null && expenseChange.abs().compareTo(BigDecimal.ONE) >= 0) {
                headlines.add("Os gastos ficaram " + percent(expenseChange)
                        + (expenseChange.signum() > 0 ? " acima" : " abaixo") + " de " + previousName + ".");
            }
            if (!top.isEmpty() && expense.signum() > 0) {
                CategoryHighlight first = top.get(0);
                headlines.add("Maior gasto: " + first.name() + ", " + percent(first.share()) + " do total ("
                        + money(first.total()) + ").");
            }
            if (biggestIncrease != null) {
                // Sobre base pequena o percentual vira ruído ("subiu 3381%"): acima de 200%, só o valor.
                BigDecimal change = biggestIncrease.changePercent();
                String increase = money(biggestIncrease.total().subtract(biggestIncrease.previousTotal()));
                headlines.add(change != null && change.compareTo(MAX_READABLE_PERCENT) <= 0
                        ? biggestIncrease.name() + " subiu " + percent(change) + " em relação a " + previousName
                                + " (+" + increase + ")."
                        : biggestIncrease.name() + " subiu " + increase + " em relação a " + previousName + ".");
            }
        }
        return new MonthlySummary(month, hasData, income, expense, net, savingsRate, previousExpense, expenseChange,
                top, biggestIncrease, headlines);
    }

    private static CategoryHighlight highlight(CategoryReportLine line) {
        return new CategoryHighlight(line.categoryId(), line.name(), line.color(), line.icon(), line.total(),
                line.share(), line.previousTotal(), line.changePercent());
    }
}
