package com.mecfin.creditcard.domain;

import com.mecfin.transaction.domain.TransactionType;
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
 * Uma cobrança (compra ou estorno) dentro de uma {@link CreditCardInvoice}. Referencia a
 * fatura e a categoria só por id (nunca relação JPA), mesmo padrão do resto do projeto.
 * {@code type} reaproveita {@code TransactionType} (INCOME para estorno, EXPENSE para compra -
 * TRANSFER não se aplica aqui) em vez de duplicar um enum equivalente - mesmo reuso que
 * {@code Bill} já faz com {@code RecurrenceRule}.
 *
 * Imutável após criada (sem {@code update}) - só existem os dois factory methods abaixo:
 * {@link #of} para uma cobrança avulsa e {@link #installmentLeg} para uma parcela de uma
 * compra parcelada (todas as parcelas materializadas de uma vez por
 * {@code CreditCardService.registerCharge}, mesmo espírito de
 * {@code TransactionService.createInstallments}).
 */
@Entity
@Table(name = "credit_card_charges")
public class CreditCardCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "credit_card_invoice_id", nullable = false, updatable = false)
    private UUID creditCardInvoiceId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "installment_number")
    private Integer installmentNumber;

    @Column(name = "installment_total")
    private Integer installmentTotal;

    @Column(name = "installment_group_id")
    private UUID installmentGroupId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CreditCardCharge() {
    }

    private CreditCardCharge(
            UUID creditCardInvoiceId,
            UUID categoryId,
            TransactionType type,
            BigDecimal amount,
            String description,
            LocalDate purchaseDate,
            Integer installmentNumber,
            Integer installmentTotal,
            UUID installmentGroupId) {
        this.creditCardInvoiceId = creditCardInvoiceId;
        this.categoryId = categoryId;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.purchaseDate = purchaseDate;
        this.installmentNumber = installmentNumber;
        this.installmentTotal = installmentTotal;
        this.installmentGroupId = installmentGroupId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static CreditCardCharge of(
            UUID creditCardInvoiceId,
            UUID categoryId,
            TransactionType type,
            BigDecimal amount,
            String description,
            LocalDate purchaseDate) {
        return new CreditCardCharge(
                creditCardInvoiceId, categoryId, type, amount, description, purchaseDate, null, null, null);
    }

    public static CreditCardCharge installmentLeg(
            UUID creditCardInvoiceId,
            UUID categoryId,
            TransactionType type,
            BigDecimal amountPerInstallment,
            String description,
            LocalDate purchaseDate,
            int installmentNumber,
            int installmentTotal,
            UUID installmentGroupId) {
        return new CreditCardCharge(
                creditCardInvoiceId, categoryId, type, amountPerInstallment, description, purchaseDate,
                installmentNumber, installmentTotal, installmentGroupId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCreditCardInvoiceId() {
        return creditCardInvoiceId;
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

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public Integer getInstallmentNumber() {
        return installmentNumber;
    }

    public Integer getInstallmentTotal() {
        return installmentTotal;
    }

    public UUID getInstallmentGroupId() {
        return installmentGroupId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
