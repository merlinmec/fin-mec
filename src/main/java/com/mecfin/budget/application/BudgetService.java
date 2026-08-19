package com.mecfin.budget.application;

import com.mecfin.account.application.AccountService;
import com.mecfin.budget.domain.Budget;
import com.mecfin.budget.infra.BudgetRepository;
import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.shared.security.CurrentUser;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import com.mecfin.transaction.infra.TransactionRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final AccountService accountService;
    private final TransactionRepository transactionRepository;

    public BudgetService(
            BudgetRepository budgetRepository,
            CategoryRepository categoryRepository,
            AccountService accountService,
            TransactionRepository transactionRepository) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public BudgetView create(UUID categoryId, YearMonth referenceMonth, BigDecimal amount) {
        UUID householdId = CurrentUser.householdId();
        validateCategory(categoryId, householdId);
        Budget budget = new Budget(householdId, categoryId, referenceMonth, amount);
        Budget saved;
        try {
            // saveAndFlush força o INSERT agora, dentro do try: uma violação da unique
            // constraint (household_id, category_id, reference_month) por criação concorrente
            // do mesmo orçamento vira 409 limpo aqui, mesmo padrão de AuthService.register.
            saved = budgetRepository.saveAndFlush(budget);
        } catch (DataIntegrityViolationException e) {
            throw new BudgetAlreadyExistsException(categoryId, referenceMonth);
        }
        return toView(saved);
    }

    public List<BudgetView> list(YearMonth referenceMonth) {
        UUID householdId = CurrentUser.householdId();
        List<Budget> budgets = referenceMonth != null
                ? budgetRepository.findAllByHouseholdIdAndReferenceMonthOrderByCreatedAtAsc(
                        householdId, referenceMonth.atDay(1))
                : budgetRepository.findAllByHouseholdIdOrderByReferenceMonthDescCreatedAtAsc(householdId);
        return budgets.stream().map(this::toView).toList();
    }

    public BudgetView get(UUID id) {
        return toView(getOwnedOrThrow(id));
    }

    @Transactional
    public BudgetView update(UUID id, BigDecimal amount) {
        Budget budget = getOwnedOrThrow(id);
        budget.updateAmount(amount);
        return toView(budget);
    }

    @Transactional
    public void delete(UUID id) {
        budgetRepository.delete(getOwnedOrThrow(id));
    }

    private Budget getOwnedOrThrow(UUID id) {
        return budgetRepository.findByIdAndHouseholdId(id, CurrentUser.householdId())
                .orElseThrow(() -> new BudgetNotFoundException(id));
    }

    private void validateCategory(UUID categoryId, UUID householdId) {
        categoryRepository.findVisibleByIdAndHouseholdId(categoryId, householdId)
                .orElseThrow(() -> new IllegalArgumentException("categoryId inválido ou não visível: " + categoryId));
    }

    // Gasto realizado nunca é persistido (ver Budget) - sempre somado de Transaction na
    // leitura, só lançamentos POSTED e do tipo EXPENSE contam.
    private BudgetView toView(Budget budget) {
        BigDecimal spent = transactionRepository.sumAmount(
                accountService.householdAccountIds(),
                budget.getCategoryId(),
                budget.getReferenceMonth().atDay(1),
                TransactionStatus.POSTED,
                TransactionType.EXPENSE);
        return new BudgetView(budget, spent);
    }
}
