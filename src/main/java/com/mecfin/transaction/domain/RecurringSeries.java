package com.mecfin.transaction.domain;

import com.mecfin.shared.domain.RecurrenceRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Molde de um lançamento fixo (aluguel, salário, assinatura). As ocorrências são
 * {@link Transaction}s comuns com {@code recurrenceSeriesId}/{@code recurrenceIndex}
 * preenchidos - a série só sabe gerar a próxima e até onde já gerou.
 *
 * {@code nextIndex} só cresce: uma ocorrência cancelada individualmente nunca é recriada,
 * porque o gerador nunca volta a um índice já emitido.
 */
@Entity
@Table(name = "recurring_series")
public class RecurringSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_rule", nullable = false, length = 20, updatable = false)
    private RecurrenceRule recurrenceRule;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_index", nullable = false)
    private int nextIndex;

    @Column(name = "active", nullable = false)
    private boolean active;

    // Lock otimista: o job diário e uma requisição do usuário podem tentar estender a mesma
    // série ao mesmo tempo; sem isso os dois poderiam reservar o mesmo nextIndex.
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RecurringSeries() {
    }

    public RecurringSeries(
            UUID householdId,
            UUID accountId,
            UUID categoryId,
            TransactionType type,
            BigDecimal amount,
            String description,
            RecurrenceRule recurrenceRule,
            LocalDate startDate,
            LocalDate endDate) {
        if (type == TransactionType.TRANSFER) {
            throw new IllegalArgumentException("Recorrência não se aplica a transferência");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("recurrenceEndDate não pode ser anterior à data do lançamento");
        }
        this.householdId = householdId;
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.recurrenceRule = recurrenceRule;
        this.startDate = startDate;
        this.endDate = endDate;
        this.nextIndex = 0;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public LocalDate occurrenceDate(int index) {
        return recurrenceRule.occurrence(startDate, index);
    }

    public LocalDate nextOccurrenceDate() {
        return occurrenceDate(nextIndex);
    }

    /** true se ainda há ocorrência a gerar com data até {@code horizon} (inclusive). */
    public boolean hasOccurrenceUntil(LocalDate horizon) {
        if (!active) {
            return false;
        }
        LocalDate next = nextOccurrenceDate();
        return !next.isAfter(horizon) && (endDate == null || !next.isAfter(endDate));
    }

    /** Reserva o próximo índice para uma ocorrência que está sendo materializada. */
    public int claimNextIndex() {
        int claimed = nextIndex;
        nextIndex++;
        touch();
        return claimed;
    }

    // Edição "esta e as próximas": o molde muda para que as ocorrências ainda não geradas
    // já nasçam com os valores novos.
    public void updateTemplate(UUID categoryId, TransactionType type, BigDecimal amount, String description) {
        this.categoryId = categoryId;
        this.type = type;
        this.amount = amount;
        this.description = description;
        touch();
    }

    /** Encerra a série: nenhuma ocorrência com data em {@code from} ou depois será gerada. */
    public void endBefore(LocalDate from) {
        LocalDate lastAllowed = from.minusDays(1);
        if (endDate == null || endDate.isAfter(lastAllowed)) {
            this.endDate = lastAllowed;
        }
        this.active = false;
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

    public RecurrenceRule getRecurrenceRule() {
        return recurrenceRule;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getNextIndex() {
        return nextIndex;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
