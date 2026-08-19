package com.mecfin.creditcard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Fatura de um {@link CreditCard} para um mês de referência. Referencia o cartão e a
 * transação de baixa só por id (nunca relação JPA), mesmo padrão do resto do projeto - ver
 * {@code Bill}.
 *
 * Sem coluna de total - o valor da fatura é sempre <b>derivado</b> somando
 * {@code credit_card_charges} (mesmo princípio de {@code Budget.spent}/{@code Bill} OVERDUE),
 * nunca persistido.
 */
@Entity
@Table(name = "credit_card_invoices")
public class CreditCardInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "credit_card_id", nullable = false, updatable = false)
    private UUID creditCardId;

    @Column(name = "reference_month", nullable = false, updatable = false)
    private LocalDate referenceMonth;

    @Column(name = "closing_date", nullable = false, updatable = false)
    private LocalDate closingDate;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CreditCardInvoiceStatus status;

    @Column(name = "paid_transaction_id")
    private UUID paidTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CreditCardInvoice() {
    }

    public CreditCardInvoice(UUID creditCardId, YearMonth referenceMonth, LocalDate closingDate, LocalDate dueDate) {
        this.creditCardId = creditCardId;
        this.referenceMonth = referenceMonth.atDay(1);
        this.closingDate = closingDate;
        this.dueDate = dueDate;
        this.status = CreditCardInvoiceStatus.OPEN;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void pay(UUID transactionId) {
        this.status = CreditCardInvoiceStatus.PAID;
        this.paidTransactionId = transactionId;
        touch();
    }

    public boolean isOpen() {
        return status == CreditCardInvoiceStatus.OPEN;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCreditCardId() {
        return creditCardId;
    }

    public YearMonth getReferenceMonth() {
        return YearMonth.from(referenceMonth);
    }

    public LocalDate getClosingDate() {
        return closingDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public CreditCardInvoiceStatus getStatus() {
        return status;
    }

    public UUID getPaidTransactionId() {
        return paidTransactionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
