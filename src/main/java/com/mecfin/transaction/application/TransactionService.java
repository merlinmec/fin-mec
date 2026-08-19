package com.mecfin.transaction.application;

import com.mecfin.account.application.AccountNotFoundException;
import com.mecfin.account.application.AccountService;
import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.shared.security.CurrentUser;
import com.mecfin.transaction.domain.Transaction;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import com.mecfin.transaction.infra.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryRepository categoryRepository;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountService accountService,
            CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.categoryRepository = categoryRepository;
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
            TransactionStatus status) {
        validateAccount(accountId);
        validateCategory(categoryId);
        TransactionStatus effectiveStatus = status != null ? status : TransactionStatus.POSTED;
        Transaction transaction = new Transaction(
                accountId, categoryId, type, amount, description, transactionDate, competenceMonth,
                effectiveStatus);
        return transactionRepository.save(transaction);
    }

    public List<Transaction> list(YearMonth competenceMonth) {
        List<UUID> accountIds = accountService.householdAccountIds();
        if (competenceMonth != null) {
            return transactionRepository.findAllByAccountIdInAndCompetenceMonthOrderByTransactionDateDescCreatedAtDesc(
                    accountIds, competenceMonth.atDay(1));
        }
        return transactionRepository.findAllByAccountIdInOrderByTransactionDateDescCreatedAtDesc(accountIds);
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
            TransactionStatus status) {
        validateCategory(categoryId);
        Transaction transaction = get(id);
        transaction.update(categoryId, type, amount, description, transactionDate, competenceMonth, status);
        return transaction;
    }

    @Transactional
    public void cancel(UUID id) {
        get(id).cancel();
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
