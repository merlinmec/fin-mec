package com.mecfin.insight.application;

import static com.mecfin.insight.application.InsightText.money;
import static com.mecfin.insight.application.InsightText.monthName;
import static com.mecfin.insight.application.InsightText.percent;

import com.mecfin.budget.application.BudgetService;
import com.mecfin.budget.application.BudgetView;
import com.mecfin.category.domain.Category;
import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.creditcard.application.CreditCardInvoiceView;
import com.mecfin.creditcard.application.CreditCardService;
import com.mecfin.creditcard.domain.CreditCard;
import com.mecfin.creditcard.domain.CreditCardInvoiceStatus;
import com.mecfin.notification.application.NotificationSource;
import com.mecfin.notification.domain.NotificationSourceType;
import com.mecfin.notification.domain.NotificationType;
import com.mecfin.report.application.CategoryReportLine;
import com.mecfin.report.application.MonthAmount;
import com.mecfin.report.application.ReportService;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Alertas inteligentes (Fase 19), entregues ao sino pela porta {@link NotificationSource}:
 * <ul>
 *   <li>orçamento do mês a 80% e estourado;</li>
 *   <li>categoria gastando 50% acima da média dos 3 meses anteriores (mês ainda em curso: se já
 *       passou da média, vai fechar pior);</li>
 *   <li>fatura em aberto 30% acima da média das últimas faturas pagas.</li>
 * </ul>
 * Limiares mínimos em reais evitam alarme por centavos (R$ 12 → R$ 20 é +66%, mas não importa).
 */
@Component
public class InsightAlertSource implements NotificationSource {

    static final BigDecimal BUDGET_WARNING_PERCENT = new BigDecimal("80");
    static final BigDecimal SPIKE_FACTOR = new BigDecimal("1.5");
    static final BigDecimal SPIKE_MIN_AVERAGE = new BigDecimal("100");
    static final BigDecimal INVOICE_FACTOR = new BigDecimal("1.3");
    static final BigDecimal INVOICE_MIN_EXCESS = new BigDecimal("50");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BudgetService budgetService;
    private final CategoryRepository categoryRepository;
    private final ReportService reportService;
    private final CreditCardService creditCardService;

    public InsightAlertSource(BudgetService budgetService, CategoryRepository categoryRepository,
            ReportService reportService, CreditCardService creditCardService) {
        this.budgetService = budgetService;
        this.categoryRepository = categoryRepository;
        this.reportService = reportService;
        this.creditCardService = creditCardService;
    }

    @Override
    public List<Candidate> candidates(LocalDate today) {
        YearMonth month = YearMonth.from(today);
        List<Candidate> out = new ArrayList<>();
        out.addAll(budgetAlerts(month));
        out.addAll(spendingSpikes(month));
        out.addAll(invoiceAlerts());
        return out;
    }

    private List<Candidate> budgetAlerts(YearMonth month) {
        List<BudgetView> budgets = budgetService.list(month);
        Map<UUID, Category> categories = categoryRepository
                .findAllById(budgets.stream().map(b -> b.budget().getCategoryId()).toList()).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        List<Candidate> out = new ArrayList<>();
        for (BudgetView view : budgets) {
            BigDecimal used = view.percentageUsed();
            if (used.compareTo(BUDGET_WARNING_PERCENT) < 0) {
                continue;
            }
            Category category = categories.get(view.budget().getCategoryId());
            String name = category != null ? category.getName() : "categoria";
            boolean exceeded = used.compareTo(HUNDRED) >= 0;
            String message = exceeded
                    ? "Orçamento de " + name + " estourado: " + money(view.spent()) + " de "
                            + money(view.budget().getAmount()) + " em " + monthName(month)
                    : "Orçamento de " + name + " em " + percent(used) + ": " + money(view.spent()) + " de "
                            + money(view.budget().getAmount());
            out.add(new Candidate(exceeded ? NotificationType.BUDGET_EXCEEDED : NotificationType.BUDGET_NEAR_LIMIT,
                    NotificationSourceType.BUDGET, view.budget().getId(), month.toString(), message));
        }
        return out;
    }

    private List<Candidate> spendingSpikes(YearMonth month) {
        List<CategoryReportLine> history = reportService
                .byCategory(month.minusMonths(3), month.minusMonths(1), TransactionType.EXPENSE).categories();
        Map<UUID, BigDecimal> current = reportService.byCategory(month, month, TransactionType.EXPENSE).categories()
                .stream()
                .filter(l -> l.categoryId() != null)
                .collect(Collectors.toMap(CategoryReportLine::categoryId, CategoryReportLine::total));
        List<Candidate> out = new ArrayList<>();
        for (CategoryReportLine past : history) {
            if (past.categoryId() == null) {
                continue;
            }
            BigDecimal now = current.get(past.categoryId());
            Candidate spike = spike(month, past, now);
            if (spike != null) {
                out.add(spike);
            }
        }
        return out;
    }

    /** Pacote-privado: a regra do pico isolada para teste de unidade. */
    static Candidate spike(YearMonth month, CategoryReportLine past, BigDecimal current) {
        long monthsWithSpending = past.monthly().stream().map(MonthAmount::amount).filter(a -> a.signum() > 0).count();
        BigDecimal average = past.monthlyAverage();
        // Média de uma categoria que apareceu num mês só não é hábito, é exceção.
        if (current == null || monthsWithSpending < 2 || average.compareTo(SPIKE_MIN_AVERAGE) < 0
                || current.compareTo(average.multiply(SPIKE_FACTOR)) < 0) {
            return null;
        }
        BigDecimal above = current.subtract(average).multiply(HUNDRED).divide(average, 0, RoundingMode.HALF_EVEN);
        String message = "Gastos com " + past.name() + " em " + monthName(month) + " já estão " + percent(above)
                + " acima da média dos últimos 3 meses (" + money(current) + " vs " + money(average) + ")";
        return new Candidate(NotificationType.CATEGORY_SPENDING_SPIKE, NotificationSourceType.CATEGORY,
                past.categoryId(), month.toString(), message);
    }

    private List<Candidate> invoiceAlerts() {
        List<Candidate> out = new ArrayList<>();
        for (CreditCard card : creditCardService.list()) {
            List<CreditCardInvoiceView> invoices = creditCardService.listInvoices(card.getId());
            List<BigDecimal> paid = invoices.stream()
                    .filter(v -> v.invoice().getStatus() == CreditCardInvoiceStatus.PAID)
                    .limit(3)
                    .map(CreditCardInvoiceView::totalAmount)
                    .toList();
            if (paid.size() < 2) {
                continue;
            }
            BigDecimal average = paid.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(paid.size()), 2, RoundingMode.HALF_EVEN);
            invoices.stream()
                    .filter(v -> v.invoice().getStatus() != CreditCardInvoiceStatus.PAID)
                    .map(v -> invoiceAlert(card, v, average))
                    .filter(Objects::nonNull)
                    .forEach(out::add);
        }
        return out;
    }

    static Candidate invoiceAlert(CreditCard card, CreditCardInvoiceView invoice, BigDecimal average) {
        BigDecimal total = invoice.totalAmount();
        if (total.compareTo(average.multiply(INVOICE_FACTOR)) < 0
                || total.subtract(average).compareTo(INVOICE_MIN_EXCESS) < 0) {
            return null;
        }
        BigDecimal above = total.subtract(average).multiply(HUNDRED).divide(average, 0, RoundingMode.HALF_EVEN);
        String message = "Fatura do " + card.getName() + " de " + monthName(invoice.invoice().getReferenceMonth())
                + " está " + percent(above) + " acima do normal: " + money(total) + " (média " + money(average) + ")";
        return new Candidate(NotificationType.CREDIT_CARD_INVOICE_ABOVE_NORMAL,
                NotificationSourceType.CREDIT_CARD_INVOICE, invoice.invoice().getId(), "", message);
    }
}
