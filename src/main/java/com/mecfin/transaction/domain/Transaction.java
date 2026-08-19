package com.mecfin.transaction.domain;

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
import java.time.YearMonth;
import java.util.UUID;

/**
 * Lançamento (receita/despesa). Referencia account e category só por id
 * (nunca relação JPA), mesmo padrão do resto do projeto. Não tem
 * {@code household_id} próprio — o household é resolvido via
 * {@code account.household_id} (ver V6__create_transactions_table.sql).
 *
 * Escopo mínimo, adiantado da Fase 5 para o cálculo de gasto realizado do
 * módulo {@code budget} ser real: sem transferência entre contas, sem
 * parcelamento, sem recorrência — isso fica para quando a Fase 5
 * completa a esses recursos.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    // Sempre o primeiro dia do mês de competência - pode diferir de transactionDate
    // (ex.: fatura de cartão fechada em um mês, competência em outro).
    @Column(name = "competence_month", nullable = false)
    private LocalDate competenceMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {
    }

    public Transaction(
            UUID accountId,
            UUID categoryId,
            TransactionType type,
            BigDecimal amount,
            String description,
            LocalDate transactionDate,
            YearMonth competenceMonth,
            TransactionStatus status) {
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.transactionDate = transactionDate;
        this.competenceMonth = competenceMonth.atDay(1);
        this.status = status;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            UUID categoryId,
            TransactionType type,
            BigDecimal amount,
            String description,
            LocalDate transactionDate,
            YearMonth competenceMonth,
            TransactionStatus status) {
        this.categoryId = categoryId;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.transactionDate = transactionDate;
        this.competenceMonth = competenceMonth.atDay(1);
        this.status = status;
        touch();
    }

    // Nunca hard-delete após POSTED - só cancela (estorno), preservando integridade de
    // relatórios e do gasto realizado já calculado por budget. Idempotente por design: cancelar
    // um PENDING ou um já-CANCELED também é seguro.
    public void cancel() {
        this.status = TransactionStatus.CANCELED;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public YearMonth getCompetenceMonth() {
        return YearMonth.from(competenceMonth);
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
