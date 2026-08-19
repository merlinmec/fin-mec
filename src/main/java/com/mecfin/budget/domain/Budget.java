package com.mecfin.budget.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Limite de gasto planejado para uma categoria num mês. Referencia
 * household e category só por id (nunca relação JPA), mesmo padrão do
 * resto do projeto. Não guarda o gasto realizado — isso é sempre
 * derivado de {@code Transaction} na leitura (ver BudgetService), para
 * não duplicar fonte de verdade.
 */
@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    // Sempre o primeiro dia do mês (YearMonth na API do domínio, LocalDate na persistência),
    // mesmo padrão de Transaction.competenceMonth.
    @Column(name = "reference_month", nullable = false, updatable = false)
    private LocalDate referenceMonth;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Budget() {
    }

    public Budget(UUID householdId, UUID categoryId, YearMonth referenceMonth, BigDecimal amount) {
        this.householdId = householdId;
        this.categoryId = categoryId;
        this.referenceMonth = referenceMonth.atDay(1);
        this.amount = amount;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // category_id e reference_month são imutáveis após criação (mudar qualquer um dos dois é,
    // na prática, um orçamento diferente) - só o valor planejado é editável.
    public void updateAmount(BigDecimal amount) {
        this.amount = amount;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public YearMonth getReferenceMonth() {
        return YearMonth.from(referenceMonth);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
