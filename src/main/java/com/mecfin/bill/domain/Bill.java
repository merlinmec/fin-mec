package com.mecfin.bill.domain;

import com.mecfin.shared.domain.RecurrenceRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Conta a pagar. Referencia household, source account, category e o lançamento de baixa só
 * por id (nunca relação JPA), mesmo padrão do resto do projeto.
 *
 * {@code status} persistido só assume OPEN/PAID/CANCELED - OVERDUE é sempre calculado na
 * leitura ({@link com.mecfin.bill.application.BillView#effectiveStatus()}), nunca gravado.
 */
@Entity
@Table(name = "bills")
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "source_account_id")
    private UUID sourceAccountId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BillStatus status;

    @Column(name = "paid_transaction_id")
    private UUID paidTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_rule", length = 20)
    private RecurrenceRule recurrenceRule;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Bill() {
    }

    public Bill(
            UUID householdId,
            String description,
            BigDecimal amount,
            LocalDate dueDate,
            UUID sourceAccountId,
            UUID categoryId,
            RecurrenceRule recurrenceRule) {
        this.householdId = householdId;
        this.description = description;
        this.amount = amount;
        this.dueDate = dueDate;
        this.sourceAccountId = sourceAccountId;
        this.categoryId = categoryId;
        this.recurrenceRule = recurrenceRule;
        this.status = BillStatus.OPEN;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // Edição só permitida enquanto OPEN - editar uma conta já paga/cancelada não faz sentido;
    // guarda aplicada em BillService (aqui a entidade só expõe a mutação em si).
    public void update(
            String description,
            BigDecimal amount,
            LocalDate dueDate,
            UUID sourceAccountId,
            UUID categoryId,
            RecurrenceRule recurrenceRule) {
        this.description = description;
        this.amount = amount;
        this.dueDate = dueDate;
        this.sourceAccountId = sourceAccountId;
        this.categoryId = categoryId;
        this.recurrenceRule = recurrenceRule;
        touch();
    }

    public void pay(UUID transactionId) {
        this.status = BillStatus.PAID;
        this.paidTransactionId = transactionId;
        touch();
    }

    public void cancel() {
        this.status = BillStatus.CANCELED;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public boolean isOpen() {
        return status == BillStatus.OPEN;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public BillStatus getStatus() {
        return status;
    }

    public UUID getPaidTransactionId() {
        return paidTransactionId;
    }

    public RecurrenceRule getRecurrenceRule() {
        return recurrenceRule;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
