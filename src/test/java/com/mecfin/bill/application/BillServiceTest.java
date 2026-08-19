package com.mecfin.bill.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.mecfin.account.application.AccountNotFoundException;
import com.mecfin.account.application.AccountService;
import com.mecfin.account.domain.Account;
import com.mecfin.account.domain.AccountType;
import com.mecfin.bill.domain.Bill;
import com.mecfin.bill.domain.BillStatus;
import com.mecfin.bill.infra.BillRepository;
import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.shared.security.AuthenticatedPrincipal;
import com.mecfin.transaction.application.TransactionService;
import com.mecfin.transaction.domain.Transaction;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
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
class BillServiceTest {

    private record TestPrincipal(UUID getUserId, UUID getHouseholdId) implements AuthenticatedPrincipal {
    }

    @Mock
    private BillRepository billRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private TransactionService transactionService;

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

    private BillService service() {
        return new BillService(billRepository, categoryRepository, accountService, transactionService);
    }

    private void stubAccountVisible(UUID id) {
        when(accountService.get(id)).thenReturn(new Account(householdId, "Conta", AccountType.CHECKING, BigDecimal.ZERO));
    }

    private Bill openBill() {
        return new Bill(householdId, "Aluguel", new BigDecimal("1500.00"), LocalDate.now().plusDays(5), accountId, null, null);
    }

    @Test
    void createScopesNewBillToCurrentHousehold() {
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BillView view = service().create("Aluguel", new BigDecimal("1500.00"), LocalDate.now().plusDays(5), null, null, null);

        assertThat(view.bill().getHouseholdId()).isEqualTo(householdId);
        assertThat(view.effectiveStatus()).isEqualTo(BillStatus.OPEN);
    }

    @Test
    void createWithInvisibleCategoryThrows() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findVisibleByIdAndHouseholdId(categoryId, householdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(
                "Aluguel", new BigDecimal("1500.00"), LocalDate.now(), null, categoryId, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithInvalidSourceAccountThrows() {
        when(accountService.get(accountId)).thenThrow(new AccountNotFoundException(accountId));

        assertThatThrownBy(() -> service().create(
                "Aluguel", new BigDecimal("1500.00"), LocalDate.now(), accountId, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listWithOverdueFilterTranslatesToOpenAndPastDueDate() {
        when(billRepository.findAllByHouseholdIdAndStatusAndDueDateBeforeOrderByDueDateAsc(
                        eq(householdId), eq(BillStatus.OPEN), any(LocalDate.class)))
                .thenReturn(List.of(openBill()));

        List<BillView> result = service().list(BillStatus.OVERDUE);

        assertThat(result).hasSize(1);
    }

    @Test
    void getThrowsBillNotFoundWhenAbsentOrFromAnotherHousehold() {
        UUID billId = UUID.randomUUID();
        when(billRepository.findByIdAndHouseholdId(billId, householdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(billId)).isInstanceOf(BillNotFoundException.class);
    }

    @Test
    void updateOfAlreadyPaidBillThrows() {
        UUID billId = UUID.randomUUID();
        Bill bill = openBill();
        bill.pay(UUID.randomUUID());
        when(billRepository.findByIdAndHouseholdId(billId, householdId)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> service().update(
                billId, "Novo", BigDecimal.TEN, LocalDate.now(), null, null, null))
                .isInstanceOf(BillNotOpenException.class);
    }

    @Test
    void payCreatesTransactionAndMarksBillPaid() {
        UUID billId = UUID.randomUUID();
        Bill bill = openBill();
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = Transaction.of(
                accountId, null, TransactionType.EXPENSE, bill.getAmount(), bill.getDescription(),
                LocalDate.now(), YearMonth.now(), TransactionStatus.POSTED, null);
        when(billRepository.findByIdAndHouseholdId(billId, householdId)).thenReturn(Optional.of(bill));
        when(transactionService.create(
                        eq(accountId), isNull(), eq(TransactionType.EXPENSE), eq(bill.getAmount()),
                        eq(bill.getDescription()), any(LocalDate.class), any(YearMonth.class),
                        eq(TransactionStatus.POSTED), isNull()))
                .thenReturn(transaction);

        BillView view = service().pay(billId, null, LocalDate.now(), null);

        assertThat(view.bill().getStatus()).isEqualTo(BillStatus.PAID);
        assertThat(view.bill().getPaidTransactionId()).isEqualTo(transaction.getId());
    }

    @Test
    void payWithoutAnyAccountThrows() {
        UUID billId = UUID.randomUUID();
        Bill bill = new Bill(householdId, "Aluguel", BigDecimal.TEN, LocalDate.now(), null, null, null);
        when(billRepository.findByIdAndHouseholdId(billId, householdId)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> service().pay(billId, null, LocalDate.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void payOfAlreadyPaidBillThrows() {
        UUID billId = UUID.randomUUID();
        Bill bill = openBill();
        bill.pay(UUID.randomUUID());
        when(billRepository.findByIdAndHouseholdId(billId, householdId)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> service().pay(billId, accountId, LocalDate.now(), null))
                .isInstanceOf(BillNotOpenException.class);
    }

    @Test
    void cancelSetsStatusToCanceled() {
        UUID billId = UUID.randomUUID();
        Bill bill = openBill();
        when(billRepository.findByIdAndHouseholdId(billId, householdId)).thenReturn(Optional.of(bill));

        service().cancel(billId);

        assertThat(bill.getStatus()).isEqualTo(BillStatus.CANCELED);
    }

    @Test
    void cancelOfAlreadyCanceledBillThrows() {
        UUID billId = UUID.randomUUID();
        Bill bill = openBill();
        bill.cancel();
        when(billRepository.findByIdAndHouseholdId(billId, householdId)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> service().cancel(billId)).isInstanceOf(BillNotOpenException.class);
    }
}
