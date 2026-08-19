package com.mecfin.dashboard.application;

import com.mecfin.account.application.AccountService;
import com.mecfin.account.domain.Account;
import com.mecfin.bill.application.BillService;
import com.mecfin.bill.application.BillView;
import com.mecfin.bill.domain.BillStatus;
import com.mecfin.budget.application.BudgetService;
import com.mecfin.budget.application.BudgetView;
import com.mecfin.category.domain.Category;
import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import com.mecfin.transaction.infra.AccountBalanceProjection;
import com.mecfin.transaction.infra.CategoryAmountProjection;
import com.mecfin.transaction.infra.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard não tem entidade nem tabela própria - é um módulo só-leitura que agrega
 * account/transaction/bill/budget (ver roadmap arquitetural, seção C).
 */
@Service
public class DashboardService {

    private static final int UPCOMING_BILLS_LIMIT = 10;

    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final BillService billService;
    private final BudgetService budgetService;
    private final CategoryRepository categoryRepository;

    public DashboardService(
            AccountService accountService,
            TransactionRepository transactionRepository,
            BillService billService,
            BudgetService budgetService,
            CategoryRepository categoryRepository) {
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
        this.billService = billService;
        this.budgetService = budgetService;
        this.categoryRepository = categoryRepository;
    }

    // readOnly=true por ser uma agregação de várias consultas sequenciais (diferente do resto
    // do projeto, que não anota leituras simples) - garante uma foto consistente dos dados
    // entre as várias queries, não só performance.
    @Transactional(readOnly = true)
    public DashboardSummary summarize(YearMonth month) {
        YearMonth referenceMonth = month != null ? month : YearMonth.now();
        LocalDate competenceMonthDate = referenceMonth.atDay(1);

        List<Account> accounts = accountService.list();
        List<UUID> accountIds = accounts.stream().map(Account::getId).toList();

        List<AccountBalance> accountBalances = buildAccountBalances(accounts, accountIds);
        BigDecimal totalLedger = sumBalances(accountBalances, AccountBalance::ledgerBalance);
        BigDecimal totalAvailable = sumBalances(accountBalances, AccountBalance::availableBalance);

        BigDecimal monthlyIncome = transactionRepository.sumAmountByMonth(
                accountIds, competenceMonthDate, TransactionStatus.POSTED, TransactionType.INCOME);
        BigDecimal monthlyExpense = transactionRepository.sumAmountByMonth(
                accountIds, competenceMonthDate, TransactionStatus.POSTED, TransactionType.EXPENSE);

        List<BillView> openBills = billService.list(BillStatus.OPEN);
        List<BillView> upcomingBills = openBills.stream()
                .sorted(Comparator.comparing(view -> view.bill().getDueDate()))
                .limit(UPCOMING_BILLS_LIMIT)
                .toList();
        BigDecimal projectedBalance = totalAvailable.subtract(pendingBillsUntil(openBills, referenceMonth.atEndOfMonth()));

        List<CategoryExpense> expensesByCategory = buildExpensesByCategory(accountIds, competenceMonthDate);
        List<BudgetView> budgets = budgetService.list(referenceMonth);

        return new DashboardSummary(
                referenceMonth, accountBalances, totalLedger, totalAvailable, monthlyIncome, monthlyExpense,
                projectedBalance, upcomingBills, expensesByCategory, budgets);
    }

    private List<AccountBalance> buildAccountBalances(List<Account> accounts, List<UUID> accountIds) {
        Map<UUID, BigDecimal> ledgerByAccount = toMap(
                transactionRepository.sumSignedAmountsByAccount(accountIds, TransactionStatus.POSTED, null));
        Map<UUID, BigDecimal> availableByAccount = toMap(
                transactionRepository.sumSignedAmountsByAccount(accountIds, TransactionStatus.POSTED, LocalDate.now()));
        return accounts.stream()
                .map(account -> new AccountBalance(
                        account.getId(),
                        account.getName(),
                        account.getType(),
                        account.getInitialBalance().add(ledgerByAccount.getOrDefault(account.getId(), BigDecimal.ZERO)),
                        account.getInitialBalance()
                                .add(availableByAccount.getOrDefault(account.getId(), BigDecimal.ZERO))))
                .toList();
    }

    private List<CategoryExpense> buildExpensesByCategory(List<UUID> accountIds, LocalDate competenceMonthDate) {
        List<CategoryAmountProjection> amounts = transactionRepository.sumGroupedByCategory(
                accountIds, competenceMonthDate, TransactionStatus.POSTED, TransactionType.EXPENSE);
        Map<UUID, Category> categoriesById = categoryRepository
                .findAllById(amounts.stream().map(CategoryAmountProjection::getCategoryId).toList())
                .stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        return amounts.stream()
                .map(projection -> new CategoryExpense(
                        projection.getCategoryId(),
                        categoryName(categoriesById, projection.getCategoryId()),
                        projection.getTotal()))
                .sorted(Comparator.comparing(CategoryExpense::amount).reversed())
                .toList();
    }

    // Categoria só some do mapa se o id não existir mais - soft delete (deletedAt) não afeta
    // findAllById, então esse fallback é uma rede de segurança defensiva, não o caminho comum.
    private String categoryName(Map<UUID, Category> categoriesById, UUID categoryId) {
        Category category = categoriesById.get(categoryId);
        return category != null ? category.getName() : "Categoria removida";
    }

    private BigDecimal pendingBillsUntil(List<BillView> openBills, LocalDate endDate) {
        return openBills.stream()
                .filter(view -> !view.bill().getDueDate().isAfter(endDate))
                .map(view -> view.bill().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumBalances(List<AccountBalance> balances, Function<AccountBalance, BigDecimal> selector) {
        return balances.stream().map(selector).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<UUID, BigDecimal> toMap(List<AccountBalanceProjection> projections) {
        return projections.stream()
                .collect(Collectors.toMap(AccountBalanceProjection::getAccountId, AccountBalanceProjection::getTotal));
    }
}
