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
import com.mecfin.transaction.domain.TransactionDirection;
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

    private void stubAccountVisible(UUID id) {
        when(accountService.get(id)).thenReturn(new Account(householdId, "Conta", AccountType.CHECKING, BigDecimal.ZERO));
    }

    @Test
    void createDefaultsStatusToPostedWhenOmitted() {
        stubAccountVisible(accountId);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction transaction = service().create(
                accountId, null, TransactionType.EXPENSE, BigDecimal.TEN, "Mercado",
                LocalDate.now(), YearMonth.now(), null, null);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(transaction.getAccountId()).isEqualTo(accountId);
    }

    @Test
    void createWithTypeTransferThrows() {
        assertThatThrownBy(() -> service().create(
                accountId, null, TransactionType.TRANSFER, BigDecimal.TEN, "Mercado",
                LocalDate.now(), YearMonth.now(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithAccountFromAnotherHouseholdThrows() {
        when(accountService.get(accountId)).thenThrow(new AccountNotFoundException(accountId));

        assertThatThrownBy(() -> service().create(
                accountId, null, TransactionType.EXPENSE, BigDecimal.TEN, "Mercado",
                LocalDate.now(), YearMonth.now(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithInvisibleCategoryThrows() {
        UUID categoryId = UUID.randomUUID();
        stubAccountVisible(accountId);
        when(categoryRepository.findVisibleByIdAndHouseholdId(categoryId, householdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(
                accountId, categoryId, TransactionType.EXPENSE, BigDecimal.TEN, "Mercado",
                LocalDate.now(), YearMonth.now(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createTransferCreatesTwoLinkedLegsOnDifferentAccounts() {
        UUID destinationAccountId = UUID.randomUUID();
        stubAccountVisible(accountId);
        stubAccountVisible(destinationAccountId);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Transaction> legs = service().createTransfer(
                accountId, destinationAccountId, BigDecimal.TEN, "Transferencia",
                LocalDate.now(), YearMonth.now(), null);

        assertThat(legs).hasSize(2);
        Transaction out = legs.get(0);
        Transaction in = legs.get(1);
        assertThat(out.getAccountId()).isEqualTo(accountId);
        assertThat(out.getTransferDirection()).isEqualTo(TransactionDirection.OUT);
        assertThat(in.getAccountId()).isEqualTo(destinationAccountId);
        assertThat(in.getTransferDirection()).isEqualTo(TransactionDirection.IN);
        assertThat(out.getTransferPairId()).isEqualTo(in.getId());
        assertThat(in.getTransferPairId()).isEqualTo(out.getId());
    }

    @Test
    void createTransferWithSameSourceAndDestinationThrows() {
        assertThatThrownBy(() -> service().createTransfer(
                accountId, accountId, BigDecimal.TEN, "Transferencia", LocalDate.now(), YearMonth.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createInstallmentsMaterializesAllLegsWithIncrementingMonth() {
        stubAccountVisible(accountId);
        when(transactionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        YearMonth firstMonth = YearMonth.of(2026, 8);

        List<Transaction> legs = service().createInstallments(
                accountId, null, TransactionType.EXPENSE, new BigDecimal("100.00"), "TV",
                LocalDate.of(2026, 8, 10), firstMonth, 3);

        assertThat(legs).hasSize(3);
        assertThat(legs.get(0).getDescription()).isEqualTo("TV (1/3)");
        assertThat(legs.get(0).getCompetenceMonth()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(legs.get(2).getDescription()).isEqualTo("TV (3/3)");
        assertThat(legs.get(2).getCompetenceMonth()).isEqualTo(YearMonth.of(2026, 10));
        UUID groupId = legs.get(0).getInstallmentGroupId();
        assertThat(groupId).isNotNull();
        assertThat(legs).allMatch(leg -> leg.getInstallmentGroupId().equals(groupId));
    }

    @Test
    void createInstallmentsWithFewerThanTwoThrows() {
        assertThatThrownBy(() -> service().createInstallments(
                accountId, null, TransactionType.EXPENSE, BigDecimal.TEN, "TV",
                LocalDate.now(), YearMonth.now(), 1))
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
    void updateOfTransferLegThrows() {
        UUID transactionId = UUID.randomUUID();
        Transaction transferLeg = Transaction.transferLeg(
                accountId, BigDecimal.TEN, "Transferencia", LocalDate.now(), YearMonth.now(),
                TransactionStatus.POSTED, TransactionDirection.OUT);
        when(accountService.householdAccountIds()).thenReturn(List.of(accountId));
        when(transactionRepository.findByIdAndAccountIdIn(transactionId, List.of(accountId)))
                .thenReturn(Optional.of(transferLeg));

        assertThatThrownBy(() -> service().update(
                transactionId, null, TransactionType.EXPENSE, BigDecimal.ONE, "Hack",
                LocalDate.now(), YearMonth.now(), TransactionStatus.POSTED, null))
                .isInstanceOf(TransferNotEditableException.class);
    }

    @Test
    void cancelSetsStatusToCanceled() {
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = Transaction.of(
                accountId, null, TransactionType.EXPENSE, BigDecimal.TEN, "Mercado",
                LocalDate.now(), YearMonth.now(), TransactionStatus.POSTED, null);
        when(accountService.householdAccountIds()).thenReturn(List.of(accountId));
        when(transactionRepository.findByIdAndAccountIdIn(transactionId, List.of(accountId)))
                .thenReturn(Optional.of(transaction));

        service().cancel(transactionId);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.CANCELED);
    }

    @Test
    void cancelCascadesToPairedTransferLeg() {
        UUID outId = UUID.randomUUID();
        UUID inId = UUID.randomUUID();
        Transaction outLeg = Transaction.transferLeg(
                accountId, BigDecimal.TEN, "Transferencia", LocalDate.now(), YearMonth.now(),
                TransactionStatus.POSTED, TransactionDirection.OUT);
        Transaction inLeg = Transaction.transferLeg(
                accountId, BigDecimal.TEN, "Transferencia", LocalDate.now(), YearMonth.now(),
                TransactionStatus.POSTED, TransactionDirection.IN);
        inLeg.linkTransferPair(outId);
        outLeg.linkTransferPair(inId);
        when(accountService.householdAccountIds()).thenReturn(List.of(accountId));
        when(transactionRepository.findByIdAndAccountIdIn(outId, List.of(accountId))).thenReturn(Optional.of(outLeg));
        when(transactionRepository.findByIdAndAccountIdIn(inId, List.of(accountId))).thenReturn(Optional.of(inLeg));

        service().cancel(outId);

        assertThat(outLeg.getStatus()).isEqualTo(TransactionStatus.CANCELED);
        assertThat(inLeg.getStatus()).isEqualTo(TransactionStatus.CANCELED);
    }
}
