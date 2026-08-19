package com.mecfin.creditcard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mecfin.account.application.AccountNotFoundException;
import com.mecfin.account.application.AccountService;
import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.creditcard.domain.CreditCard;
import com.mecfin.creditcard.domain.CreditCardCharge;
import com.mecfin.creditcard.domain.CreditCardInvoice;
import com.mecfin.creditcard.domain.CreditCardInvoiceStatus;
import com.mecfin.creditcard.infra.CreditCardChargeRepository;
import com.mecfin.creditcard.infra.CreditCardInvoiceRepository;
import com.mecfin.creditcard.infra.CreditCardRepository;
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
class CreditCardServiceTest {

    private record TestPrincipal(UUID getUserId, UUID getHouseholdId) implements AuthenticatedPrincipal {
    }

    @Mock
    private CreditCardRepository creditCardRepository;

    @Mock
    private CreditCardInvoiceRepository invoiceRepository;

    @Mock
    private CreditCardChargeRepository chargeRepository;

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

    private CreditCardService service() {
        return new CreditCardService(
                creditCardRepository, invoiceRepository, chargeRepository, categoryRepository, accountService,
                transactionService);
    }

    private CreditCard newCard() {
        return new CreditCard(householdId, "Nubank", new BigDecimal("5000.00"), 10, 17, null);
    }

    @Test
    void createScopesNewCardToCurrentHousehold() {
        when(creditCardRepository.save(any(CreditCard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreditCard card = service().create("Nubank", new BigDecimal("5000.00"), 10, 17, null);

        assertThat(card.getHouseholdId()).isEqualTo(householdId);
        assertThat(card.isArchived()).isFalse();
    }

    @Test
    void createWithInvalidPaymentAccountThrows() {
        when(accountService.get(accountId)).thenThrow(new AccountNotFoundException(accountId));

        assertThatThrownBy(() -> service().create("Nubank", new BigDecimal("5000.00"), 10, 17, accountId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getThrowsCreditCardNotFoundWhenAbsentOrFromAnotherHousehold() {
        UUID cardId = UUID.randomUUID();
        when(creditCardRepository.findByIdAndHouseholdIdAndDeletedAtIsNull(cardId, householdId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(cardId)).isInstanceOf(CreditCardNotFoundException.class);
    }

    @Test
    void registerChargeWithTransferTypeThrows() {
        UUID cardId = UUID.randomUUID();

        assertThatThrownBy(() -> service().registerCharge(
                cardId, null, TransactionType.TRANSFER, BigDecimal.TEN, "X", LocalDate.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registerChargeWithInstallmentsLessThanTwoThrows() {
        UUID cardId = UUID.randomUUID();
        when(creditCardRepository.findByIdAndHouseholdIdAndDeletedAtIsNull(cardId, householdId))
                .thenReturn(Optional.of(newCard()));

        assertThatThrownBy(() -> service().registerCharge(
                cardId, null, TransactionType.EXPENSE, BigDecimal.TEN, "X", LocalDate.now(), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registerChargeWithInvisibleCategoryThrows() {
        UUID cardId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(creditCardRepository.findByIdAndHouseholdIdAndDeletedAtIsNull(cardId, householdId))
                .thenReturn(Optional.of(newCard()));
        when(categoryRepository.findVisibleByIdAndHouseholdId(categoryId, householdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().registerCharge(
                cardId, categoryId, TransactionType.EXPENSE, BigDecimal.TEN, "Mercado", LocalDate.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registerChargeCreatesSingleChargeOnResolvedInvoice() {
        UUID cardId = UUID.randomUUID();
        when(creditCardRepository.findByIdAndHouseholdIdAndDeletedAtIsNull(cardId, householdId))
                .thenReturn(Optional.of(newCard()));
        when(invoiceRepository.findByCreditCardIdAndReferenceMonth(any(), any())).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(CreditCardInvoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chargeRepository.save(any(CreditCardCharge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<CreditCardCharge> charges = service().registerCharge(
                cardId, null, TransactionType.EXPENSE, new BigDecimal("120.00"), "Mercado", LocalDate.now(), null);

        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).getInstallmentNumber()).isNull();
        assertThat(charges.get(0).getAmount()).isEqualByComparingTo("120.00");
    }

    @Test
    void registerChargeWithInstallmentsCreatesAllLegsWithSharedGroupId() {
        UUID cardId = UUID.randomUUID();
        when(creditCardRepository.findByIdAndHouseholdIdAndDeletedAtIsNull(cardId, householdId))
                .thenReturn(Optional.of(newCard()));
        when(invoiceRepository.findByCreditCardIdAndReferenceMonth(any(), any())).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(CreditCardInvoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chargeRepository.save(any(CreditCardCharge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<CreditCardCharge> charges = service().registerCharge(
                cardId, null, TransactionType.EXPENSE, new BigDecimal("100.00"), "Notebook", LocalDate.now(), 3);

        assertThat(charges).hasSize(3);
        assertThat(charges).extracting(CreditCardCharge::getInstallmentNumber).containsExactly(1, 2, 3);
        UUID groupId = charges.get(0).getInstallmentGroupId();
        assertThat(groupId).isNotNull();
        assertThat(charges).allSatisfy(charge -> assertThat(charge.getInstallmentGroupId()).isEqualTo(groupId));
    }

    @Test
    void payInvoiceCreatesTransactionAndMarksInvoicePaid() {
        UUID cardId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        CreditCard card = newCard();
        CreditCardInvoice invoice = new CreditCardInvoice(
                cardId, YearMonth.now(), LocalDate.now().plusDays(5), LocalDate.now().plusDays(12));
        when(creditCardRepository.findAllByHouseholdId(householdId)).thenReturn(List.of(card));
        when(invoiceRepository.findByIdAndCreditCardIdIn(eq(invoiceId), any())).thenReturn(Optional.of(invoice));
        when(creditCardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(chargeRepository.findAllByCreditCardInvoiceIdOrderByPurchaseDateAsc(invoiceId)).thenReturn(List.of(
                CreditCardCharge.of(
                        invoiceId, null, TransactionType.EXPENSE, new BigDecimal("150.00"), "Mercado",
                        LocalDate.now())));
        Transaction transaction = Transaction.of(
                accountId, null, TransactionType.EXPENSE, new BigDecimal("150.00"), "Fatura", LocalDate.now(),
                YearMonth.now(), TransactionStatus.POSTED, null);
        when(transactionService.create(
                        eq(accountId), isNull(), eq(TransactionType.EXPENSE), eq(new BigDecimal("150.00")),
                        any(String.class), any(LocalDate.class), any(YearMonth.class), eq(TransactionStatus.POSTED),
                        isNull()))
                .thenReturn(transaction);

        CreditCardInvoiceView view = service().payInvoice(invoiceId, accountId, LocalDate.now(), null);

        assertThat(view.invoice().getStatus()).isEqualTo(CreditCardInvoiceStatus.PAID);
        assertThat(view.invoice().getPaidTransactionId()).isEqualTo(transaction.getId());
    }

    @Test
    void payInvoiceWithoutAnyAccountThrows() {
        UUID cardId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        CreditCard card = newCard();
        CreditCardInvoice invoice = new CreditCardInvoice(
                cardId, YearMonth.now(), LocalDate.now().plusDays(5), LocalDate.now().plusDays(12));
        when(creditCardRepository.findAllByHouseholdId(householdId)).thenReturn(List.of(card));
        when(invoiceRepository.findByIdAndCreditCardIdIn(eq(invoiceId), any())).thenReturn(Optional.of(invoice));
        when(creditCardRepository.findById(cardId)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service().payInvoice(invoiceId, null, LocalDate.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void payAlreadyPaidInvoiceThrows() {
        UUID invoiceId = UUID.randomUUID();
        CreditCardInvoice invoice = new CreditCardInvoice(
                UUID.randomUUID(), YearMonth.now(), LocalDate.now().plusDays(5), LocalDate.now().plusDays(12));
        invoice.pay(UUID.randomUUID());
        when(creditCardRepository.findAllByHouseholdId(householdId)).thenReturn(List.of(newCard()));
        when(invoiceRepository.findByIdAndCreditCardIdIn(eq(invoiceId), any())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service().payInvoice(invoiceId, accountId, LocalDate.now(), null))
                .isInstanceOf(CreditCardInvoiceNotOpenException.class);
    }

    @Test
    void getInvoiceThrowsWhenAbsentOrFromAnotherHousehold() {
        UUID invoiceId = UUID.randomUUID();
        when(creditCardRepository.findAllByHouseholdId(householdId)).thenReturn(List.of());
        when(invoiceRepository.findByIdAndCreditCardIdIn(eq(invoiceId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getInvoice(invoiceId)).isInstanceOf(CreditCardInvoiceNotFoundException.class);
    }

    @Test
    void deleteChargeOnOpenInvoiceRemovesIt() {
        UUID invoiceId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        CreditCardInvoice invoice = new CreditCardInvoice(
                UUID.randomUUID(), YearMonth.now(), LocalDate.now().plusDays(5), LocalDate.now().plusDays(12));
        CreditCardCharge charge = CreditCardCharge.of(
                invoiceId, null, TransactionType.EXPENSE, BigDecimal.TEN, "X", LocalDate.now());
        when(creditCardRepository.findAllByHouseholdId(householdId)).thenReturn(List.of(newCard()));
        when(invoiceRepository.findByIdAndCreditCardIdIn(eq(invoiceId), any())).thenReturn(Optional.of(invoice));
        when(chargeRepository.findByIdAndCreditCardInvoiceId(chargeId, invoiceId)).thenReturn(Optional.of(charge));

        service().deleteCharge(invoiceId, chargeId);

        verify(chargeRepository).delete(charge);
    }

    @Test
    void deleteChargeOnPaidInvoiceThrows() {
        UUID invoiceId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        CreditCardInvoice invoice = new CreditCardInvoice(
                UUID.randomUUID(), YearMonth.now(), LocalDate.now().plusDays(5), LocalDate.now().plusDays(12));
        invoice.pay(UUID.randomUUID());
        when(creditCardRepository.findAllByHouseholdId(householdId)).thenReturn(List.of(newCard()));
        when(invoiceRepository.findByIdAndCreditCardIdIn(eq(invoiceId), any())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service().deleteCharge(invoiceId, chargeId))
                .isInstanceOf(CreditCardInvoiceNotOpenException.class);
    }
}
