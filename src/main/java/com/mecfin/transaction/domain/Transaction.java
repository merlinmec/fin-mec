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
 * Lançamento (receita/despesa/transferência). Referencia account e category só por id
 * (nunca relação JPA), mesmo padrão do resto do projeto. Não tem {@code household_id}
 * próprio - o household é resolvido via {@code account.household_id}.
 *
 * Três "formatos" possíveis, expostos como factory methods nomeados em vez de um único
 * construtor com uma dezena de parâmetros quase sempre nulos:
 * <ul>
 *   <li>{@link #of} - lançamento avulso (receita/despesa), com recorrência opcional (só
 *       metadado, ver {@link RecurrenceRule}).</li>
 *   <li>{@link #transferLeg} - uma perna de transferência entre contas; sempre criada em
 *       par por {@code TransactionService.createTransfer}, nunca sozinha.</li>
 *   <li>{@link #installmentLeg} - uma parcela de uma compra parcelada; todas as parcelas
 *       são materializadas de uma vez por {@code TransactionService.createInstallments}.</li>
 * </ul>
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

    // Sem updatable=false de propósito (diferente dos outros campos de transferência/parcela
    // abaixo): a perna criada primeiro precisa de um UPDATE pós-insert genuíno pra apontar pra
    // segunda perna (referência mútua, nenhuma das duas tem o id da outra no insert). A
    // imutabilidade pretendida é garantida no nível de serviço (só linkTransferPair muta isso,
    // update() não toca aqui), não pelo mapeamento JPA.
    @Column(name = "transfer_pair_id")
    private UUID transferPairId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_direction", length = 10, updatable = false)
    private TransactionDirection transferDirection;

    @Column(name = "installment_number", updatable = false)
    private Integer installmentNumber;

    @Column(name = "installment_total", updatable = false)
    private Integer installmentTotal;

    @Column(name = "installment_group_id", updatable = false)
    private UUID installmentGroupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_rule", length = 20)
    private RecurrenceRule recurrenceRule;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {
    }

    private Transaction(
            UUID accountId,
            UUID categoryId,
            TransactionType type,
            BigDecimal amount,
            String description,
            LocalDate transactionDate,
            YearMonth competenceMonth,
            TransactionStatus status,
            RecurrenceRule recurrenceRule,
            TransactionDirection transferDirection,
            Integer installmentNumber,
            Integer installmentTotal,
            UUID installmentGroupId) {
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.transactionDate = transactionDate;
        this.competenceMonth = competenceMonth.atDay(1);
        this.status = status;
        this.recurrenceRule = recurrenceRule;
        this.transferDirection = transferDirection;
        this.installmentNumber = installmentNumber;
        this.installmentTotal = installmentTotal;
        this.installmentGroupId = installmentGroupId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Transaction of(
            UUID accountId,
            UUID categoryId,
            TransactionType type,
            BigDecimal amount,
            String description,
            LocalDate transactionDate,
            YearMonth competenceMonth,
            TransactionStatus status,
            RecurrenceRule recurrenceRule) {
        return new Transaction(accountId, categoryId, type, amount, description, transactionDate, competenceMonth,
                status, recurrenceRule, null, null, null, null);
    }

    public static Transaction transferLeg(
            UUID accountId,
            BigDecimal amount,
            String description,
            LocalDate transactionDate,
            YearMonth competenceMonth,
            TransactionStatus status,
            TransactionDirection direction) {
        return new Transaction(accountId, null, TransactionType.TRANSFER, amount, description, transactionDate,
                competenceMonth, status, null, direction, null, null, null);
    }

    public static Transaction installmentLeg(
            UUID accountId,
            UUID categoryId,
            TransactionType type,
            BigDecimal amount,
            String description,
            LocalDate transactionDate,
            YearMonth competenceMonth,
            TransactionStatus status,
            int installmentNumber,
            int installmentTotal,
            UUID installmentGroupId) {
        return new Transaction(accountId, categoryId, type, amount, description, transactionDate, competenceMonth,
                status, null, null, installmentNumber, installmentTotal, installmentGroupId);
    }

    // Chamado só por TransactionService.createTransfer, depois que as duas pernas já têm id
    // (transferPairId é uma referência mútua - não dá pra setar no construtor de nenhuma
    // das duas). Público por convenção do projeto (nenhuma entidade daqui usa visibilidade
    // package-private, ver Account/Category), não por falta de encapsulamento pretendido.
    public void linkTransferPair(UUID pairId) {
        this.transferPairId = pairId;
        touch();
    }

    // category/type/amount/descrição/data seguem editáveis; campos estruturais de
    // transferência e parcelamento (transferPairId, installment*) são imutáveis por design -
    // ver TransactionService.update, que bloqueia edição de lançamento TRANSFER inteiro.
    public void update(
            UUID categoryId,
            TransactionType type,
            BigDecimal amount,
            String description,
            LocalDate transactionDate,
            YearMonth competenceMonth,
            TransactionStatus status,
            RecurrenceRule recurrenceRule) {
        this.categoryId = categoryId;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.transactionDate = transactionDate;
        this.competenceMonth = competenceMonth.atDay(1);
        this.status = status;
        this.recurrenceRule = recurrenceRule;
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

    public UUID getTransferPairId() {
        return transferPairId;
    }

    public TransactionDirection getTransferDirection() {
        return transferDirection;
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
