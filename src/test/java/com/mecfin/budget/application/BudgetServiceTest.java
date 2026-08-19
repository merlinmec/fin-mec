package com.mecfin.budget.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mecfin.account.application.AccountService;
import com.mecfin.budget.domain.Budget;
import com.mecfin.budget.infra.BudgetRepository;
import com.mecfin.category.domain.Category;
import com.mecfin.category.domain.CategoryType;
import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.shared.security.AuthenticatedPrincipal;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import com.mecfin.transaction.infra.TransactionRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private record TestPrincipal(UUID getUserId, UUID getHouseholdId) implements AuthenticatedPrincipal {
    }

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private TransactionRepository transactionRepository;

    private final UUID householdId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final YearMonth referenceMonth = YearMonth.of(2026, 8);

    @BeforeEach
    void authenticateAsHousehold() {
        AuthenticatedPrincipal principal = new TestPrincipal(UUID.randomUUID(), householdId);
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private BudgetService service() {
        return new BudgetService(budgetRepository, categoryRepository, accountService, transactionRepository);
    }

    private void stubCategoryVisible() {
        Category category = new Category(householdId, "Lazer", CategoryType.EXPENSE, null, null, null);
        when(categoryRepository.findVisibleByIdAndHouseholdId(categoryId, householdId))
                .thenReturn(Optional.of(category));
    }

    @Test
    void createScopesNewBudgetToCurrentHouseholdAndComputesSpent() {
        stubCategoryVisible();
        when(budgetRepository.saveAndFlush(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountService.householdAccountIds()).thenReturn(List.of(UUID.randomUUID()));
        when(transactionRepository.sumAmount(
                        any(), eq(categoryId), any(), eq(TransactionStatus.POSTED), eq(TransactionType.EXPENSE)))
                .thenReturn(new BigDecimal("30.00"));

        BudgetView view = service().create(categoryId, referenceMonth, new BigDecimal("100.00"));

        assertThat(view.budget().getHouseholdId()).isEqualTo(householdId);
        assertThat(view.spent()).isEqualByComparingTo("30.00");
        assertThat(view.percentageUsed()).isEqualByComparingTo("30.0000");
    }

    @Test
    void createWithInvisibleCategoryThrows() {
        when(categoryRepository.findVisibleByIdAndHouseholdId(categoryId, householdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(categoryId, referenceMonth, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getThrowsBudgetNotFoundWhenAbsentOrFromAnotherHousehold() {
        UUID budgetId = UUID.randomUUID();
        when(budgetRepository.findByIdAndHouseholdId(budgetId, householdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(budgetId)).isInstanceOf(BudgetNotFoundException.class);
    }

    @Test
    void deleteRemovesOwnBudget() {
        UUID budgetId = UUID.randomUUID();
        Budget budget = new Budget(householdId, categoryId, referenceMonth, BigDecimal.TEN);
        when(budgetRepository.findByIdAndHouseholdId(budgetId, householdId)).thenReturn(Optional.of(budget));

        service().delete(budgetId);

        verify(budgetRepository).delete(budget);
    }
}
