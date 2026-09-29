package com.mecfin.report.application;

import com.mecfin.account.application.AccountService;
import com.mecfin.account.domain.Account;
import com.mecfin.category.domain.Category;
import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import com.mecfin.transaction.infra.AccountBalanceProjection;
import com.mecfin.transaction.infra.CategoryMonthTotalProjection;
import com.mecfin.transaction.infra.MonthlyFlowProjection;
import com.mecfin.transaction.infra.MonthlyTypeTotalProjection;
import com.mecfin.transaction.infra.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relatórios (Fase 12). Como o dashboard, não tem tabela própria: tudo é derivado de
 * transaction/account/category na leitura. Todo percentual, média e variação é calculado aqui -
 * o frontend só desenha (princípio do PRODUCT.md: nunca recalcular número no cliente).
 */
@Service
public class ReportService {

    public static final int MAX_MONTHS = 24;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    // Sentinela para o balde "sem categoria" dentro dos mapas (Collectors.groupingBy não aceita
    // chave nula). Nunca sai deste serviço: vira categoryId null na resposta.
    private static final UUID NO_CATEGORY = new UUID(0, 0);

    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public ReportService(
            AccountService accountService,
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository) {
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Fluxo de caixa mês a mês + evolução do saldo. O saldo de fechamento de cada mês usa as
     * mesmas contas e a mesma regra do saldo contábil do dashboard (contas não excluídas, saldo
     * inicial + lançamentos efetivados até o último dia do mês).
     */
    @Transactional(readOnly = true)
    public CashFlowReport cashFlow(YearMonth from, YearMonth to) {
        validateRange(from, to);
        List<Account> accounts = accountService.list();
        List<UUID> accountIds = accounts.stream().map(Account::getId).toList();

        Map<YearMonth, Map<TransactionType, Map<TransactionStatus, BigDecimal>>> totals = new HashMap<>();
        for (MonthlyTypeTotalProjection row : transactionRepository.sumByMonthTypeAndStatus(
                accountIds, from.atDay(1), to.atDay(1))) {
            totals.computeIfAbsent(YearMonth.from(row.getMonth()), m -> new HashMap<>())
                    .computeIfAbsent(row.getType(), t -> new HashMap<>())
                    .put(row.getStatus(), row.getTotal());
        }

        Map<YearMonth, BigDecimal> flowByMonth = transactionRepository
                .sumSignedByTransactionMonth(accountIds, from.atDay(1), to.atEndOfMonth()).stream()
                .collect(Collectors.toMap(row -> YearMonth.of(row.getYear(), row.getMonth()),
                        MonthlyFlowProjection::getTotal));

        BigDecimal opening = accounts.stream().map(Account::getInitialBalance).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(transactionRepository.sumSignedAmountsByAccount(
                                accountIds, TransactionStatus.POSTED, from.atDay(1).minusDays(1)).stream()
                        .map(AccountBalanceProjection::getTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

        List<CashFlowMonth> months = new ArrayList<>();
        BigDecimal running = opening;
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            Map<TransactionType, Map<TransactionStatus, BigDecimal>> byType = totals.getOrDefault(month, Map.of());
            BigDecimal income = amount(byType, TransactionType.INCOME, TransactionStatus.POSTED);
            BigDecimal expense = amount(byType, TransactionType.EXPENSE, TransactionStatus.POSTED);
            running = running.add(flowByMonth.getOrDefault(month, BigDecimal.ZERO));
            months.add(new CashFlowMonth(month, income, expense, income.subtract(expense),
                    amount(byType, TransactionType.INCOME, TransactionStatus.PENDING),
                    amount(byType, TransactionType.EXPENSE, TransactionStatus.PENDING),
                    running));
        }

        BigDecimal totalIncome = sum(months, CashFlowMonth::income);
        BigDecimal totalExpense = sum(months, CashFlowMonth::expense);
        BigDecimal net = totalIncome.subtract(totalExpense);
        BigDecimal averageExpense = totalExpense.divide(BigDecimal.valueOf(months.size()), 2, RoundingMode.HALF_EVEN);
        BigDecimal savingsRate = totalIncome.signum() == 0 ? null : percent(net, totalIncome);
        return new CashFlowReport(from, to, opening, running, totalIncome, totalExpense, net, averageExpense,
                savingsRate, months);
    }

    /**
     * Gastos (ou receitas) por categoria no período, com participação no total, série mensal e
     * comparação com o período imediatamente anterior de mesmo tamanho.
     */
    @Transactional(readOnly = true)
    public CategoryReport byCategory(YearMonth from, YearMonth to, TransactionType type) {
        validateRange(from, to);
        if (type == TransactionType.TRANSFER) {
            throw new IllegalArgumentException("Relatório por categoria aceita só INCOME ou EXPENSE");
        }
        List<UUID> accountIds = accountService.list().stream().map(Account::getId).toList();
        long length = from.until(to, ChronoUnit.MONTHS) + 1;
        YearMonth previousFrom = from.minusMonths(length);
        YearMonth previousTo = from.minusMonths(1);

        List<CategoryMonthTotalProjection> current = transactionRepository.sumByCategoryAndMonth(
                accountIds, from.atDay(1), to.atDay(1), type);
        Map<UUID, BigDecimal> previousByCategory = transactionRepository.sumByCategoryAndMonth(
                        accountIds, previousFrom.atDay(1), previousTo.atDay(1), type).stream()
                .collect(Collectors.groupingBy(row -> Objects.requireNonNullElse(row.getCategoryId(), NO_CATEGORY),
                        Collectors.reducing(BigDecimal.ZERO, CategoryMonthTotalProjection::getTotal, BigDecimal::add)));

        Map<UUID, Map<YearMonth, BigDecimal>> monthlyByCategory = new LinkedHashMap<>();
        for (CategoryMonthTotalProjection row : current) {
            monthlyByCategory
                    .computeIfAbsent(Objects.requireNonNullElse(row.getCategoryId(), NO_CATEGORY), c -> new HashMap<>())
                    .merge(YearMonth.from(row.getMonth()), row.getTotal(), BigDecimal::add);
        }

        Map<UUID, Category> categories = categoryRepository.findAllById(monthlyByCategory.keySet()).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        BigDecimal total = monthlyByCategory.values().stream()
                .flatMap(m -> m.values().stream())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previousTotal = previousByCategory.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategoryReportLine> lines = monthlyByCategory.entrySet().stream()
                .map(entry -> {
                    UUID categoryId = NO_CATEGORY.equals(entry.getKey()) ? null : entry.getKey();
                    Category category = categoryId != null ? categories.get(categoryId) : null;
                    BigDecimal categoryTotal = entry.getValue().values().stream()
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal previous = previousByCategory.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                    List<MonthAmount> monthly = new ArrayList<>();
                    for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
                        monthly.add(new MonthAmount(m, entry.getValue().getOrDefault(m, BigDecimal.ZERO)));
                    }
                    return new CategoryReportLine(
                            categoryId,
                            category != null ? category.getName() : categoryId == null ? "Sem categoria" : "Categoria removida",
                            category != null ? category.getColor() : null,
                            category != null ? category.getIcon() : null,
                            categoryTotal,
                            total.signum() == 0 ? BigDecimal.ZERO : percent(categoryTotal, total),
                            previous,
                            change(categoryTotal, previous),
                            categoryTotal.divide(BigDecimal.valueOf(length), 2, RoundingMode.HALF_EVEN),
                            monthly);
                })
                .sorted(Comparator.comparing(CategoryReportLine::total).reversed())
                .toList();

        return new CategoryReport(from, to, type, total, previousTotal, change(total, previousTotal), lines);
    }

    private static void validateRange(YearMonth from, YearMonth to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' não pode ser posterior a 'to'");
        }
        if (from.plusMonths(MAX_MONTHS - 1L).isBefore(to)) {
            throw new IllegalArgumentException("Período máximo do relatório é de " + MAX_MONTHS + " meses");
        }
    }

    private static BigDecimal amount(
            Map<TransactionType, Map<TransactionStatus, BigDecimal>> byType, TransactionType type,
            TransactionStatus status) {
        return byType.getOrDefault(type, Map.of()).getOrDefault(status, BigDecimal.ZERO);
    }

    private static BigDecimal sum(List<CashFlowMonth> months, Function<CashFlowMonth, BigDecimal> selector) {
        return months.stream().map(selector).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_EVEN);
    }

    // Variação percentual contra o período anterior; null quando não há base (anterior zero) -
    // "+∞%" não é informação útil, a UI mostra "novo" nesse caso.
    private static BigDecimal change(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return null;
        }
        return percent(current.subtract(previous), previous);
    }
}
