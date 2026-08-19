package com.mecfin.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.mecfin.account.application.AccountService;
import com.mecfin.account.domain.Account;
import com.mecfin.account.domain.AccountType;
import com.mecfin.bill.application.BillService;
import com.mecfin.bill.application.BillView;
import com.mecfin.bill.domain.Bill;
import com.mecfin.bill.domain.BillStatus;
import com.mecfin.budget.application.BudgetService;
import com.mecfin.category.domain.Category;
import com.mecfin.category.domain.CategoryType;
import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.infra.AccountBalanceProjection;
import com.mecfin.transaction.infra.CategoryAmountProjection;
import com.mecfin.transaction.infra.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private AccountService accountService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BillService billService;

    @Mock
    private BudgetService budgetService;

    @Mock
    private CategoryRepository categoryRepository;

    private DashboardService service() {
        return new DashboardService(accountService, transactionRepository, billService, budgetService, categoryRepository);
    }

    private record FakeAccountBalance(UUID accountId, BigDecimal total) implements AccountBalanceProjection {
        public UUID getAccountId() {
            return accountId;
        }

        public BigDecimal getTotal() {
            return total;
        }
    }

    private record FakeCategoryAmount(UUID categoryId, BigDecimal total) implements CategoryAmountProjection {
        public UUID getCategoryId() {
            return categoryId;
        }

        public BigDecimal getTotal() {
            return total;
        }
    }

    @Test
    void accountBalanceCombinesInitialBalanceWithLedgerAndAvailableProjections() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(UUID.randomUUID(), "Conta", AccountType.CHECKING, new BigDecimal("100.00"));
        ReflectionTestUtils.setField(account, "id", accountId); // simula o id gerado pelo save() real
        when(accountService.list()).thenReturn(List.of(account));
        when(transactionRepository.sumSignedAmountsByAccount(any(), eq(TransactionStatus.POSTED), isNull()))
                .thenReturn(List.of(new FakeAccountBalance(accountId, new BigDecimal("50.00"))));
        when(transactionRepository.sumSignedAmountsByAccount(any(), eq(TransactionStatus.POSTED), any(LocalDate.class)))
                .thenReturn(List.of(new FakeAccountBalance(accountId, new BigDecimal("30.00"))));
        when(transactionRepository.sumAmountByMonth(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(billService.list(BillStatus.OPEN)).thenReturn(List.of());
        when(transactionRepository.sumGroupedByCategory(any(), any(), any(), any())).thenReturn(List.of());
        when(budgetService.list(any())).thenReturn(List.of());

        DashboardSummary summary = service().summarize(YearMonth.of(2026, 8));

        assertThat(summary.accountBalances()).hasSize(1);
        AccountBalance balance = summary.accountBalances().get(0);
        assertThat(balance.ledgerBalance()).isEqualByComparingTo("150.00");
        assertThat(balance.availableBalance()).isEqualByComparingTo("130.00");
        assertThat(summary.totalLedgerBalance()).isEqualByComparingTo("150.00");
        assertThat(summary.totalAvailableBalance()).isEqualByComparingTo("130.00");
    }

    @Test
    void projectedBalanceSubtractsOnlyOpenBillsDueUntilEndOfReferenceMonth() {
        when(accountService.list()).thenReturn(List.of());
        when(transactionRepository.sumSignedAmountsByAccount(any(), any(), any())).thenReturn(List.of());
        when(transactionRepository.sumAmountByMonth(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        Bill dueThisMonth = new Bill(UUID.randomUUID(), "Aluguel", new BigDecimal("500.00"), LocalDate.of(2026, 8, 20), null, null, null);
        Bill dueNextMonth = new Bill(UUID.randomUUID(), "Seguro", new BigDecimal("999.00"), LocalDate.of(2026, 9, 1), null, null, null);
        when(billService.list(BillStatus.OPEN)).thenReturn(List.of(new BillView(dueThisMonth), new BillView(dueNextMonth)));
        when(transactionRepository.sumGroupedByCategory(any(), any(), any(), any())).thenReturn(List.of());
        when(budgetService.list(any())).thenReturn(List.of());

        DashboardSummary summary = service().summarize(YearMonth.of(2026, 8));

        assertThat(summary.projectedBalance()).isEqualByComparingTo("-500.00");
        assertThat(summary.upcomingBills()).hasSize(2);
    }

    @Test
    void expensesByCategoryResolvesCategoryNameAndSortsDescending() {
        when(accountService.list()).thenReturn(List.of());
        when(transactionRepository.sumSignedAmountsByAccount(any(), any(), any())).thenReturn(List.of());
        when(transactionRepository.sumAmountByMonth(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(billService.list(BillStatus.OPEN)).thenReturn(List.of());
        UUID categoryId1 = UUID.randomUUID();
        UUID categoryId2 = UUID.randomUUID();
        Category category1 = new Category(UUID.randomUUID(), "Lazer", CategoryType.EXPENSE, null, null, null);
        ReflectionTestUtils.setField(category1, "id", categoryId1); // simula o id gerado pelo save() real
        when(transactionRepository.sumGroupedByCategory(any(), any(), any(), any())).thenReturn(List.of(
                new FakeCategoryAmount(categoryId1, new BigDecimal("50.00")),
                new FakeCategoryAmount(categoryId2, new BigDecimal("200.00"))));
        when(categoryRepository.findAllById(any())).thenReturn(List.of(category1));
        when(budgetService.list(any())).thenReturn(List.of());

        DashboardSummary summary = service().summarize(YearMonth.of(2026, 8));

        assertThat(summary.expensesByCategory()).hasSize(2);
        // categoryId2 (200.00) vem primeiro - ordenado desc por valor.
        assertThat(summary.expensesByCategory().get(0).amount()).isEqualByComparingTo("200.00");
        assertThat(summary.expensesByCategory().get(0).categoryName()).isEqualTo("Categoria removida");
        assertThat(summary.expensesByCategory().get(1).categoryName()).isEqualTo("Lazer");
    }
}
