package com.mecfin.transaction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mecfin.account.application.AccountNotFoundException;
import com.mecfin.account.application.AccountService;
import com.mecfin.account.domain.Account;
import com.mecfin.account.domain.AccountType;
import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.shared.security.AuthenticatedPrincipal;
import com.mecfin.transaction.domain.Transaction;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import com.mecfin.transaction.infra.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class TransactionServiceTest {

    private record TestPrincipal(UUID getUserId, UUID getHouseholdId) implements AuthenticatedPrincipal {
    }

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private CategoryRepository categoryRepository;

    private final UUID householdId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

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

    private TransactionService service() {
        return new TransactionService(transactionRepository, accountService, categoryRepository);
    }

    @Test
    void createDefaultsStatusToPostedWhenOmitted() {
        when(accountService.get(accountId))
                .thenReturn(new Account(householdId, "Conta", AccountType.CHECKING, BigDecimal.ZERO));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction transaction = service().create(
                accountId, null, TransactionType.EXPENSE, BigDecimal.TEN, "Mercado",
                LocalDate.now(), YearMonth.now(), null);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(transaction.getAccountId()).isEqualTo(accountId);
    }

    @Test
    void createWithAccountFromAnotherHouseholdThrows() {
        when(accountService.get(accountId)).thenThrow(new AccountNotFoundException(accountId));

        assertThatThrownBy(() -> service().create(
                accountId, null, TransactionType.EXPENSE, BigDecimal.TEN, "Mercado",
                LocalDate.now(), YearMonth.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithInvisibleCategoryThrows() {
        UUID categoryId = UUID.randomUUID();
        when(accountService.get(accountId))
                .thenReturn(new Account(householdId, "Conta", AccountType.CHECKING, BigDecimal.ZERO));
        when(categoryRepository.findVisibleByIdAndHouseholdId(categoryId, householdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(
                accountId, categoryId, TransactionType.EXPENSE, BigDecimal.TEN, "Mercado",
                LocalDate.now(), YearMonth.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getThrowsTransactionNotFoundWhenAbsentOrFromAnotherHousehold() {
        UUID transactionId = UUID.randomUUID();
        when(accountService.householdAccountIds()).thenReturn(List.of(accountId));
        when(transactionRepository.findByIdAndAccountIdIn(transactionId, List.of(accountId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(transactionId)).isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void cancelSetsStatusToCanceled() {
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = new Transaction(
                accountId, null, TransactionType.EXPENSE, BigDecimal.TEN, "Mercado",
                LocalDate.now(), YearMonth.now(), TransactionStatus.POSTED);
        when(accountService.householdAccountIds()).thenReturn(List.of(accountId));
        when(transactionRepository.findByIdAndAccountIdIn(transactionId, List.of(accountId)))
                .thenReturn(Optional.of(transaction));

        service().cancel(transactionId);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.CANCELED);
    }
}
