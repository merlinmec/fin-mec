package com.mecfin.account.domain;

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
import java.util.UUID;

/**
 * Conta financeira manual (corrente, poupança, carteira, investimento).
 * Referencia o household só por id ({@code householdId}) — nunca uma relação
 * JPA para {@code Household} — para manter os módulos financeiros
 * desacoplados do módulo household no nível de persistência.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AccountType type;

    @Column(name = "initial_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal initialBalance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
    }

    public Account(UUID householdId, String name, AccountType type, BigDecimal initialBalance) {
        this.householdId = householdId;
        this.name = name;
        this.type = type;
        this.initialBalance = initialBalance;
        this.currency = "BRL";
        this.archived = false;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, AccountType type, boolean archived) {
        this.name = name;
        this.type = type;
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

    public AccountType getType() {
        return type;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public String getCurrency() {
        return currency;
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
