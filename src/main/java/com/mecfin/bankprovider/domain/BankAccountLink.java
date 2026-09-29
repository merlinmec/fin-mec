package com.mecfin.bankprovider.domain;

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

/** Uma conta do banco conectado e a conta do produto que recebe os lançamentos dela. */
@Entity
@Table(name = "bank_account_links")
public class BankAccountLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "bank_connection_id", nullable = false, updatable = false)
    private UUID bankConnectionId;

    @Column(name = "external_account_id", nullable = false, updatable = false)
    private String externalAccountId;

    @Column(name = "account_id")
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private BankAccountLinkMode mode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "number", length = 60)
    private String number;

    @Column(name = "bank_balance", precision = 19, scale = 4)
    private BigDecimal bankBalance;

    @Column(name = "bank_balance_at")
    private Instant bankBalanceAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BankAccountLink() {
    }

    public BankAccountLink(UUID householdId, UUID bankConnectionId, String externalAccountId) {
        this.householdId = householdId;
        this.bankConnectionId = bankConnectionId;
        this.externalAccountId = externalAccountId;
        this.mode = BankAccountLinkMode.PENDING;
        this.name = "Conta";
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void refresh(String name, String number, BigDecimal balance, Instant at) {
        this.name = name == null || name.isBlank() ? "Conta" : truncate(name, 255);
        this.number = number == null ? null : truncate(number, 60);
        this.bankBalance = balance;
        this.bankBalanceAt = at;
        this.updatedAt = Instant.now();
    }

    public void linkTo(UUID accountId) {
        this.accountId = accountId;
        this.mode = BankAccountLinkMode.LINKED;
        this.updatedAt = Instant.now();
    }

    public void ignore() {
        this.accountId = null;
        this.mode = BankAccountLinkMode.IGNORED;
        this.updatedAt = Instant.now();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getBankConnectionId() {
        return bankConnectionId;
    }

    public String getExternalAccountId() {
        return externalAccountId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public BankAccountLinkMode getMode() {
        return mode;
    }

    public String getName() {
        return name;
    }

    public String getNumber() {
        return number;
    }

    public BigDecimal getBankBalance() {
        return bankBalance;
    }

    public Instant getBankBalanceAt() {
        return bankBalanceAt;
    }
}
