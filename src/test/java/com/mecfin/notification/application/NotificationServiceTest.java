package com.mecfin.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mecfin.bill.domain.Bill;
import com.mecfin.bill.domain.BillStatus;
import com.mecfin.bill.infra.BillRepository;
import com.mecfin.creditcard.domain.CreditCard;
import com.mecfin.creditcard.domain.CreditCardInvoice;
import com.mecfin.creditcard.infra.CreditCardInvoiceRepository;
import com.mecfin.creditcard.infra.CreditCardRepository;
import com.mecfin.notification.domain.Notification;
import com.mecfin.notification.domain.NotificationSourceType;
import com.mecfin.notification.domain.NotificationType;
import com.mecfin.notification.infra.NotificationRepository;
import com.mecfin.shared.security.AuthenticatedPrincipal;
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
class NotificationServiceTest {

    private record TestPrincipal(UUID getUserId, UUID getHouseholdId) implements AuthenticatedPrincipal {
    }

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private BillRepository billRepository;

    @Mock
    private CreditCardRepository creditCardRepository;

    @Mock
    private CreditCardInvoiceRepository creditCardInvoiceRepository;

    private final UUID householdId = UUID.randomUUID();

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

    private NotificationService service() {
        return new NotificationService(
                notificationRepository, billRepository, creditCardRepository, creditCardInvoiceRepository);
    }

    private Bill bill(LocalDate dueDate) {
        return new Bill(householdId, "Aluguel", new BigDecimal("1500.00"), dueDate, null, null, null);
    }

    @Test
    void syncCreatesDueSoonNotificationForBillWithinThreshold() {
        Bill bill = bill(LocalDate.now().plusDays(2));
        when(billRepository.findAllByHouseholdIdAndStatusOrderByDueDateAsc(householdId, BillStatus.OPEN))
                .thenReturn(List.of(bill));
        when(notificationRepository.findByHouseholdIdAndTypeAndSourceId(
                        eq(householdId), eq(NotificationType.BILL_DUE_SOON), any())).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationRepository.findAllByHouseholdIdOrderByCreatedAtDesc(householdId)).thenReturn(List.of());

        service().sync();

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void syncCreatesOverdueNotificationForPastDueBill() {
        Bill bill = bill(LocalDate.now().minusDays(1));
        when(billRepository.findAllByHouseholdIdAndStatusOrderByDueDateAsc(householdId, BillStatus.OPEN))
                .thenReturn(List.of(bill));
        when(notificationRepository.findByHouseholdIdAndTypeAndSourceId(
                        eq(householdId), eq(NotificationType.BILL_OVERDUE), any())).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            assertThat(saved.getType()).isEqualTo(NotificationType.BILL_OVERDUE);
            assertThat(saved.getSourceType()).isEqualTo(NotificationSourceType.BILL);
            return saved;
        });
        when(notificationRepository.findAllByHouseholdIdOrderByCreatedAtDesc(householdId)).thenReturn(List.of());

        service().sync();

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void syncIgnoresBillsFarInFuture() {
        Bill bill = bill(LocalDate.now().plusDays(10));
        when(billRepository.findAllByHouseholdIdAndStatusOrderByDueDateAsc(householdId, BillStatus.OPEN))
                .thenReturn(List.of(bill));
        when(notificationRepository.findAllByHouseholdIdOrderByCreatedAtDesc(householdId)).thenReturn(List.of());

        service().sync();

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void syncDoesNotDuplicateExistingNotification() {
        Bill bill = bill(LocalDate.now().plusDays(1));
        when(billRepository.findAllByHouseholdIdAndStatusOrderByDueDateAsc(householdId, BillStatus.OPEN))
                .thenReturn(List.of(bill));
        when(notificationRepository.findByHouseholdIdAndTypeAndSourceId(
                        eq(householdId), eq(NotificationType.BILL_DUE_SOON), any()))
                .thenReturn(Optional.of(new Notification(
                        householdId, NotificationType.BILL_DUE_SOON, NotificationSourceType.BILL, bill.getId(),
                        "já existe")));
        when(notificationRepository.findAllByHouseholdIdOrderByCreatedAtDesc(householdId)).thenReturn(List.of());

        service().sync();

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void syncCreatesNotificationForCreditCardInvoiceDueSoon() {
        UUID cardId = UUID.randomUUID();
        CreditCard card = new CreditCard(householdId, "Nubank", new BigDecimal("5000.00"), 10, 17, null);
        CreditCardInvoice invoice = new CreditCardInvoice(
                cardId, YearMonth.now(), LocalDate.now().minusDays(5), LocalDate.now().plusDays(1));
        when(billRepository.findAllByHouseholdIdAndStatusOrderByDueDateAsc(householdId, BillStatus.OPEN))
                .thenReturn(List.of());
        when(creditCardRepository.findAllByHouseholdIdAndDeletedAtIsNullOrderByNameAsc(householdId))
                .thenReturn(List.of(card));
        when(creditCardInvoiceRepository.findAllByCreditCardIdOrderByReferenceMonthDesc(card.getId()))
                .thenReturn(List.of(invoice));
        when(notificationRepository.findByHouseholdIdAndTypeAndSourceId(
                        eq(householdId), eq(NotificationType.CREDIT_CARD_INVOICE_DUE_SOON), eq(invoice.getId())))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationRepository.findAllByHouseholdIdOrderByCreatedAtDesc(householdId)).thenReturn(List.of());

        service().sync();

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void listWithNullReadReturnsAll() {
        when(notificationRepository.findAllByHouseholdIdOrderByCreatedAtDesc(householdId)).thenReturn(List.of());

        service().list(null);

        verify(notificationRepository).findAllByHouseholdIdOrderByCreatedAtDesc(householdId);
        verify(notificationRepository, never()).findAllByHouseholdIdAndReadOrderByCreatedAtDesc(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void listWithReadFalseFiltersUnread() {
        when(notificationRepository.findAllByHouseholdIdAndReadOrderByCreatedAtDesc(householdId, false))
                .thenReturn(List.of());

        service().list(false);

        verify(notificationRepository).findAllByHouseholdIdAndReadOrderByCreatedAtDesc(householdId, false);
    }

    @Test
    void markReadMarksNotificationAsRead() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = new Notification(
                householdId, NotificationType.BILL_DUE_SOON, NotificationSourceType.BILL, UUID.randomUUID(), "msg");
        when(notificationRepository.findByIdAndHouseholdId(notificationId, householdId))
                .thenReturn(Optional.of(notification));

        Notification result = service().markRead(notificationId);

        assertThat(result.isRead()).isTrue();
        assertThat(result.getReadAt()).isNotNull();
    }

    @Test
    void markReadThrowsWhenAbsentOrFromAnotherHousehold() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findByIdAndHouseholdId(notificationId, householdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().markRead(notificationId))
                .isInstanceOf(NotificationNotFoundException.class);
    }
}
