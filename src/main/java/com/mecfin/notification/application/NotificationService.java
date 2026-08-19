package com.mecfin.notification.application;

import com.mecfin.bill.domain.Bill;
import com.mecfin.bill.domain.BillStatus;
import com.mecfin.bill.infra.BillRepository;
import com.mecfin.creditcard.domain.CreditCard;
import com.mecfin.creditcard.domain.CreditCardInvoice;
import com.mecfin.creditcard.domain.CreditCardInvoiceStatus;
import com.mecfin.creditcard.infra.CreditCardInvoiceRepository;
import com.mecfin.creditcard.infra.CreditCardRepository;
import com.mecfin.notification.domain.Notification;
import com.mecfin.notification.domain.NotificationSourceType;
import com.mecfin.notification.domain.NotificationType;
import com.mecfin.notification.infra.NotificationRepository;
import com.mecfin.shared.security.CurrentUser;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notificações in-app de vencimento (contas a pagar e faturas de cartão) - e-mail fica pra
 * depois, ver roadmap ("in-app primeiro; e-mail depois").
 *
 * <p>{@link #sync()} gera as notificações pendentes <b>sob demanda</b> (chamada explícita do
 * cliente, ex.: ao abrir o app), em vez de um job agendado (`@Scheduled`) rodando em
 * background - decisão deliberada: evita introduzir infraestrutura de cron pela primeira vez
 * no projeto (mesmo princípio de "sem Kubernetes/Kafka/Redis/filas - nenhum requisito atual
 * justifica" do doc de arquitetura). {@code GET /notifications} continua puramente de leitura;
 * quem decide quando sincronizar é o cliente via {@code POST /notifications/sync}.
 */
@Service
public class NotificationService {

    // Janela de antecedência pra gerar um DUE_SOON - sem configurabilidade no MVP, mesmo
    // espírito de outras simplificações deliberadas do projeto (ex.: recorrência só metadado).
    private static final int DUE_SOON_DAYS = 3;

    private final NotificationRepository notificationRepository;
    private final BillRepository billRepository;
    private final CreditCardRepository creditCardRepository;
    private final CreditCardInvoiceRepository creditCardInvoiceRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            BillRepository billRepository,
            CreditCardRepository creditCardRepository,
            CreditCardInvoiceRepository creditCardInvoiceRepository) {
        this.notificationRepository = notificationRepository;
        this.billRepository = billRepository;
        this.creditCardRepository = creditCardRepository;
        this.creditCardInvoiceRepository = creditCardInvoiceRepository;
    }

    @Transactional
    public List<Notification> sync() {
        UUID householdId = CurrentUser.householdId();
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(DUE_SOON_DAYS);

        for (Bill bill : billRepository.findAllByHouseholdIdAndStatusOrderByDueDateAsc(householdId, BillStatus.OPEN)) {
            if (bill.getDueDate().isAfter(threshold)) {
                continue;
            }
            NotificationType type = bill.getDueDate().isBefore(today)
                    ? NotificationType.BILL_OVERDUE
                    : NotificationType.BILL_DUE_SOON;
            upsert(householdId, type, NotificationSourceType.BILL, bill.getId(), billMessage(bill, type, today));
        }

        for (CreditCard card : creditCardRepository.findAllByHouseholdIdAndDeletedAtIsNullOrderByNameAsc(householdId)) {
            for (CreditCardInvoice invoice : creditCardInvoiceRepository.findAllByCreditCardIdOrderByReferenceMonthDesc(card.getId())) {
                if (invoice.getStatus() != CreditCardInvoiceStatus.OPEN || invoice.getDueDate().isAfter(threshold)) {
                    continue;
                }
                NotificationType type = invoice.getDueDate().isBefore(today)
                        ? NotificationType.CREDIT_CARD_INVOICE_OVERDUE
                        : NotificationType.CREDIT_CARD_INVOICE_DUE_SOON;
                upsert(householdId, type, NotificationSourceType.CREDIT_CARD_INVOICE, invoice.getId(),
                        invoiceMessage(card, invoice, type, today));
            }
        }

        return list(null);
    }

    // read == null lista tudo; true/false filtra lida/não lida - mesmo espírito do filtro
    // opcional de BillService.list(status).
    public List<Notification> list(Boolean read) {
        UUID householdId = CurrentUser.householdId();
        if (read == null) {
            return notificationRepository.findAllByHouseholdIdOrderByCreatedAtDesc(householdId);
        }
        return notificationRepository.findAllByHouseholdIdAndReadOrderByCreatedAtDesc(householdId, read);
    }

    @Transactional
    public Notification markRead(UUID id) {
        Notification notification = notificationRepository.findByIdAndHouseholdId(id, CurrentUser.householdId())
                .orElseThrow(() -> new NotificationNotFoundException(id));
        notification.markRead();
        return notification;
    }

    private void upsert(
            UUID householdId, NotificationType type, NotificationSourceType sourceType, UUID sourceId,
            String message) {
        if (notificationRepository.findByHouseholdIdAndTypeAndSourceId(householdId, type, sourceId).isPresent()) {
            return;
        }
        notificationRepository.save(new Notification(householdId, type, sourceType, sourceId, message));
    }

    private String billMessage(Bill bill, NotificationType type, LocalDate today) {
        if (type == NotificationType.BILL_OVERDUE) {
            return "Conta \"" + bill.getDescription() + "\" está vencida desde " + bill.getDueDate();
        }
        long days = ChronoUnit.DAYS.between(today, bill.getDueDate());
        return "Conta \"" + bill.getDescription() + "\" vence em " + days + " dia(s)";
    }

    private String invoiceMessage(CreditCard card, CreditCardInvoice invoice, NotificationType type, LocalDate today) {
        if (type == NotificationType.CREDIT_CARD_INVOICE_OVERDUE) {
            return "Fatura do cartão \"" + card.getName() + "\" está vencida desde " + invoice.getDueDate();
        }
        long days = ChronoUnit.DAYS.between(today, invoice.getDueDate());
        return "Fatura do cartão \"" + card.getName() + "\" vence em " + days + " dia(s)";
    }
}
