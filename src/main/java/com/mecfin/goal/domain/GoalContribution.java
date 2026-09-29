package com.mecfin.goal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// Aporte (amount > 0) ou resgate (amount < 0) numa meta. Imutável: corrigir = excluir e lançar
// de novo, como um extrato.
@Entity
@Table(name = "goal_contributions")
public class GoalContribution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "goal_id", nullable = false, updatable = false)
    private UUID goalId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "contribution_date", nullable = false, updatable = false)
    private LocalDate contributionDate;

    @Column(name = "note", length = 255, updatable = false)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GoalContribution() {
    }

    public GoalContribution(UUID goalId, BigDecimal amount, LocalDate contributionDate, String note) {
        if (amount.signum() == 0) {
            throw new IllegalArgumentException("Valor do aporte não pode ser zero");
        }
        this.goalId = goalId;
        this.amount = amount;
        this.contributionDate = contributionDate;
        this.note = note;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getGoalId() {
        return goalId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getContributionDate() {
        return contributionDate;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
