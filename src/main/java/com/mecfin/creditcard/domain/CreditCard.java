package com.mecfin.creditcard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cartão de crédito. Referencia household e a conta de pagamento padrão só por id (nunca
 * relação JPA), mesmo padrão do resto do projeto - ver {@code Account}.
 *
 * {@code closingDay}/{@code dueDay} são dias do mês (1-31, validados no request via Bean
 * Validation) usados por {@code CreditCardService.resolveOrCreateInvoice} para decidir em qual
 * fatura uma cobrança cai.
 */
@Entity
@Table(name = "credit_cards")
public class CreditCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "credit_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(name = "closing_day", nullable = false)
    private int closingDay;

    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Column(name = "payment_account_id")
    private UUID paymentAccountId;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CreditCard() {
    }

    public CreditCard(
            UUID householdId, String name, BigDecimal creditLimit, int closingDay, int dueDay,
            UUID paymentAccountId) {
        this.householdId = householdId;
        this.name = name;
        this.creditLimit = creditLimit;
        this.closingDay = closingDay;
        this.dueDay = dueDay;
        this.paymentAccountId = paymentAccountId;
        this.archived = false;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String name, BigDecimal creditLimit, int closingDay, int dueDay, UUID paymentAccountId,
            boolean archived) {
        this.name = name;
        this.creditLimit = creditLimit;
        this.closingDay = closingDay;
        this.dueDay = dueDay;
        this.paymentAccountId = paymentAccountId;
        this.archived = archived;
        touch();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public int getClosingDay() {
        return closingDay;
    }

    public int getDueDay() {
        return dueDay;
    }

    public UUID getPaymentAccountId() {
        return paymentAccountId;
    }

    public boolean isArchived() {
        return archived;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
