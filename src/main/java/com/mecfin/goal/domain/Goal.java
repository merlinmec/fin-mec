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

/**
 * Meta de economia (Fase 13): "cofrinho" virtual com valor-alvo e prazo opcional. O quanto já
 * foi guardado nunca é persistido aqui - é a soma dos {@link GoalContribution}s, derivada na
 * leitura (mesmo princípio de Budget.spent).
 */
@Entity
@Table(name = "goals")
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "color", length = 7)
    private String color;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Goal() {
    }

    public Goal(UUID householdId, String name, BigDecimal targetAmount, LocalDate targetDate, String color, String icon) {
        this.householdId = householdId;
        this.name = name.strip();
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
        this.color = color;
        this.icon = icon;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, BigDecimal targetAmount, LocalDate targetDate, String color, String icon,
            boolean archived) {
        this.name = name.strip();
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
        this.color = color;
        this.icon = icon;
        if (archived && archivedAt == null) {
            this.archivedAt = Instant.now();
        } else if (!archived) {
            this.archivedAt = null;
        }
        this.updatedAt = Instant.now();
    }

    public boolean isArchived() {
        return archivedAt != null;
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

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public String getColor() {
        return color;
    }

    public String getIcon() {
        return icon;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
