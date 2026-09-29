package com.mecfin.transaction.application;

import com.mecfin.account.application.AccountNotFoundException;
import com.mecfin.account.application.AccountService;
import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.shared.domain.RecurrenceRule;
import com.mecfin.shared.security.CurrentUser;
import com.mecfin.transaction.domain.RecurringSeries;
import com.mecfin.transaction.domain.Transaction;
import com.mecfin.transaction.domain.TransactionDirection;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import com.mecfin.transaction.infra.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryRepository categoryRepository;
    private final RecurringSeriesService recurringSeriesService;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountService accountService,
            CategoryRepository categoryRepository,
            RecurringSeriesService recurringSeriesService) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.categoryRepository = categoryRepository;
        this.recurringSeriesService = recurringSeriesService;
    }

    // Sobrecarga sem data final - usada por quem não lida com recorrência (ex.: BillService).
    @Transactional
    public Transaction create(
            UUID accountId,
            UUID categoryId,
            TransactionType type,
            BigDecimal amount,
            String description,
            LocalDate transactionDate,
            YearMonth competenceMonth,
            TransactionStatus status,
            RecurrenceRule recurrenceRule) {
        return create(accountId, categoryId, type, amount, description, transactionDate, competenceMonth, status,
                recurrenceRule, null);
    }

    @Transactional
    public Transaction create(
            UUID accountId,
            UUID categoryId,
            TransactionType type,
            BigDecimal amount,
            String description,
            LocalDate transactionDate,
            YearMonth competenceMonth,
            TransactionStatus status,
            RecurrenceRule recurrenceRule,
            LocalDate recurrenceEndDate) {
        if (type == TransactionType.TRANSFER) {
            throw new IllegalArgumentException("Use POST /transactions/transfers para criar uma transferência");
        }
        validateAccount(accountId);
        validateCategory(categoryId);
        if (recurrenceRule != null) {
            RecurringSeries series = new RecurringSeries(CurrentUser.householdId(), accountId, categoryId, type,
                    amount, description, recurrenceRule, transactionDate, recurrenceEndDate);
            return recurringSeriesService.start(series, effectiveStatus(status), competenceMonth);
        }
        if (recurrenceEndDate != null) {
            throw new IllegalArgumentException("recurrenceEndDate só faz sentido junto com recurrenceRule");
        }
        Transaction transaction = Transaction.of(
                accountId, categoryId, type, amount, description, transactionDate, competenceMonth,
                effectiveStatus(status), null);
        return transactionRepository.save(transaction);
    }

    // Cria as duas pernas atomicamente (mesma transação de banco). transferPairId é uma
    // referência mútua - nenhuma das duas pernas tem o id da outra até ambas existirem, por
    // isso a primeira é salva, linkada na segunda, e depois a primeira é linkada de volta.
    @Transactional
    public List<Transaction> createTransfer(
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String description,
            LocalDate transactionDate,
            YearMonth competenceMonth,
            TransactionStatus status) {
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("sourceAccountId e destinationAccountId não podem ser a mesma conta");
        }
        validateAccount(sourceAccountId);
        validateAccount(destinationAccountId);
        TransactionStatus effective = effectiveStatus(status);

        Transaction outLeg = Transaction.transferLeg(
                sourceAccountId, amount, description, transactionDate, competenceMonth, effective,
                TransactionDirection.OUT);
        Transaction savedOut = transactionRepository.save(outLeg);

        Transaction inLeg = Transaction.transferLeg(
                destinationAccountId, amount, description, transactionDate, competenceMonth, effective,
                TransactionDirection.IN);
        inLeg.linkTransferPair(savedOut.getId());
        Transaction savedIn = transactionRepository.save(inLeg);

        savedOut.linkTransferPair(savedIn.getId());
        // save() explícito por clareza (dirty-checking do Hibernate já cobriria isso no commit
        // da transação, já que savedOut segue managed) - o bug real de um UPDATE que não
        // persistia era updatable=false no mapeamento da coluna, corrigido em Transaction.java.
        transactionRepository.save(savedOut);
        return List.of(savedOut, savedIn);
    }

    // Materializa todas as N parcelas de uma vez (diferente de recorrência, que é aberta e por
    // isso é materializada aos poucos numa janela deslizante - ver RecurringSeriesService).
    @Transactional
    public List<Transaction> createInstallments(
            UUID accountId,
            UUID categoryId,
            TransactionType type,
            BigDecimal amountPerInstallment,
            String description,
            LocalDate firstTransactionDate,
            YearMonth firstCompetenceMonth,
            int installments) {
        if (type == TransactionType.TRANSFER) {
            throw new IllegalArgumentException("Parcelamento não se aplica a transferência");
        }
        if (installments < 2) {
            throw new IllegalArgumentException("installments deve ser >= 2 (use POST /transactions para um lançamento avulso)");
        }
        validateAccount(accountId);
        validateCategory(categoryId);

        UUID groupId = UUID.randomUUID();
        List<Transaction> legs = new ArrayList<>();
        for (int i = 1; i <= installments; i++) {
            LocalDate date = firstTransactionDate.plusMonths(i - 1L);
            YearMonth month = firstCompetenceMonth.plusMonths(i - 1L);
            String numberedDescription = description + " (" + i + "/" + installments + ")";
            legs.add(Transaction.installmentLeg(
                    accountId, categoryId, type, amountPerInstallment, numberedDescription, date, month,
                    TransactionStatus.POSTED, i, installments, groupId));
        }
        return transactionRepository.saveAll(legs);
    }

    public Page<Transaction> search(TransactionFilter filter, Pageable pageable) {
        List<UUID> accountIds = accountService.householdAccountIds();
        LocalDate competenceMonthDate = filter.competenceMonth() != null ? filter.competenceMonth().atDay(1) : null;
        return transactionRepository.search(accountIds, filter.accountId(), filter.categoryId(), filter.type(),
                filter.status(), competenceMonthDate, filter.from(), filter.to(), filter.likePattern(),
                filter.minAmount(), filter.maxAmount(), pageable);
    }

    public Transaction get(UUID id) {
        return transactionRepository.findByIdAndAccountIdIn(id, accountService.householdAccountIds())
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    @Transactional
    public Transaction update(
            UUID id,
            UUID categoryId,
            TransactionType type,
            BigDecimal amount,
            String description,
            LocalDate transactionDate,
            YearMonth competenceMonth,
            TransactionStatus status,
            RecurrenceRule recurrenceRule,
            EditScope scope) {
        Transaction transaction = get(id);
        if (transaction.getType() == TransactionType.TRANSFER) {
            throw new TransferNotEditableException(id);
        }
        validateCategory(categoryId);
        if (transaction.isRecurrenceOccurrence()) {
            // A periodicidade de uma ocorrência vem da série; mudar a regra é encerrar a série e
            // criar outra, não editar uma ocorrência.
            transaction.update(categoryId, type, amount, description, transactionDate, competenceMonth, status,
                    transaction.getRecurrenceRule());
            if (scope == EditScope.THIS_AND_FUTURE) {
                recurringSeriesService.applyTemplateFrom(transaction);
            }
            return transaction;
        }
        if (scope == EditScope.THIS_AND_FUTURE) {
            throw new IllegalArgumentException("scope=THIS_AND_FUTURE só se aplica a lançamento fixo");
        }
        // Só vira fixo quando a regra passa de vazia para preenchida. Lançamento criado até a
        // Fase 10 pode ter recurrence_rule como metadado puro; reenviar o mesmo valor numa edição
        // comum não pode gerar uma série que o usuário nunca pediu.
        boolean becomesRecurring = recurrenceRule != null && transaction.getRecurrenceRule() == null;
        transaction.update(categoryId, type, amount, description, transactionDate, competenceMonth, status,
                becomesRecurring ? null : recurrenceRule);
        if (becomesRecurring) {
            if (transaction.getInstallmentGroupId() != null) {
                throw new IllegalArgumentException("Parcela não pode virar lançamento fixo");
            }
            recurringSeriesService.startFrom(transaction, new RecurringSeries(CurrentUser.householdId(),
                    transaction.getAccountId(), categoryId, type, amount, description, recurrenceRule,
                    transactionDate, null));
        }
        return transaction;
    }

    // Efetiva um lançamento previsto (tipicamente uma ocorrência futura de um fixo).
    @Transactional
    public Transaction confirm(UUID id, BigDecimal actualAmount, LocalDate actualDate) {
        Transaction transaction = get(id);
        transaction.confirm(actualAmount, actualDate);
        if (transaction.getTransferPairId() != null) {
            transactionRepository.findByIdAndAccountIdIn(transaction.getTransferPairId(),
                            accountService.householdAccountIds())
                    .ifPresent(pair -> pair.confirm(actualAmount, actualDate));
        }
        return transaction;
    }

    // Cancela as duas pernas juntas quando é uma transferência - cancelar só um lado deixaria
    // dinheiro "sumindo" de uma conta sem aparecer na outra.
    @Transactional
    public void cancel(UUID id, EditScope scope) {
        Transaction transaction = get(id);
        if (scope == EditScope.THIS_AND_FUTURE) {
            if (!transaction.isRecurrenceOccurrence()) {
                throw new IllegalArgumentException("scope=THIS_AND_FUTURE só se aplica a lançamento fixo");
            }
            recurringSeriesService.cancelFrom(transaction);
            return;
        }
        transaction.cancel();
        if (transaction.getTransferPairId() != null) {
            transactionRepository.findByIdAndAccountIdIn(transaction.getTransferPairId(), accountService.householdAccountIds())
                    .ifPresent(Transaction::cancel);
        }
    }

    private TransactionStatus effectiveStatus(TransactionStatus status) {
        return status != null ? status : TransactionStatus.POSTED;
    }

    // accountId é campo de payload (não o recurso primário do endpoint) - inválido/não visível
    // vira 400, não 404, mesmo padrão de CategoryService.validateParent.
    private void validateAccount(UUID accountId) {
        try {
            accountService.get(accountId);
        } catch (AccountNotFoundException e) {
            throw new IllegalArgumentException("accountId inválido ou não visível: " + accountId);
        }
    }

    private void validateCategory(UUID categoryId) {
        if (categoryId == null) {
            return;
        }
        categoryRepository.findVisibleByIdAndHouseholdId(categoryId, CurrentUser.householdId())
                .orElseThrow(() -> new IllegalArgumentException("categoryId inválido ou não visível: " + categoryId));
    }
}
