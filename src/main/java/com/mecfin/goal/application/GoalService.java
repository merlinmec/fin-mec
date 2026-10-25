package com.mecfin.goal.application;

import com.mecfin.account.application.AccountNotFoundException;
import com.mecfin.account.application.AccountService;
import com.mecfin.goal.domain.Goal;
import com.mecfin.goal.domain.GoalContribution;
import com.mecfin.goal.infra.GoalAmountProjection;
import com.mecfin.goal.infra.GoalContributionRepository;
import com.mecfin.goal.infra.GoalRepository;
import com.mecfin.shared.security.CurrentUser;
import com.mecfin.transaction.application.EditScope;
import com.mecfin.transaction.application.TransactionService;
import com.mecfin.transaction.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalContributionRepository contributionRepository;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final Clock clock;

    public GoalService(GoalRepository goalRepository, GoalContributionRepository contributionRepository,
            AccountService accountService, TransactionService transactionService, Clock clock) {
        this.goalRepository = goalRepository;
        this.contributionRepository = contributionRepository;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.clock = clock;
    }

    @Transactional
    public GoalView create(String name, BigDecimal targetAmount, LocalDate targetDate, String color, String icon,
            UUID accountId) {
        Goal goal = new Goal(CurrentUser.householdId(), name, targetAmount, targetDate, color, icon);
        goal.linkAccount(validateAccount(accountId));
        goal = goalRepository.save(goal);
        return GoalView.of(goal, BigDecimal.ZERO, BigDecimal.ZERO, today());
    }

    @Transactional(readOnly = true)
    public List<GoalView> list(boolean includeArchived) {
        List<Goal> goals = goalRepository.findAllByHouseholdIdOrderByCreatedAtAsc(CurrentUser.householdId()).stream()
                .filter(goal -> includeArchived || !goal.isArchived())
                .toList();
        if (goals.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = goals.stream().map(Goal::getId).toList();
        Map<UUID, BigDecimal> saved = toMap(contributionRepository.sumByGoal(ids));
        Map<UUID, BigDecimal> thisMonth = toMap(contributionRepository.sumByGoalSince(ids, today().withDayOfMonth(1)));
        return goals.stream()
                .map(goal -> GoalView.of(goal, saved.getOrDefault(goal.getId(), BigDecimal.ZERO),
                        thisMonth.getOrDefault(goal.getId(), BigDecimal.ZERO), today()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GoalView get(UUID id) {
        return view(getOwnedOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<GoalContribution> contributions(UUID goalId) {
        return contributionRepository.findAllByGoalIdOrderByContributionDateDescCreatedAtDesc(getOwnedOrThrow(goalId).getId());
    }

    @Transactional
    public GoalView update(UUID id, String name, BigDecimal targetAmount, LocalDate targetDate, String color,
            String icon, boolean archived, UUID accountId) {
        Goal goal = getOwnedOrThrow(id);
        goal.update(name, targetAmount, targetDate, color, icon, archived);
        goal.linkAccount(validateAccount(accountId));
        return view(goal);
    }

    // Hard delete: os aportes vão junto (ON DELETE CASCADE). Meta não movimenta conta, então não
    // há saldo nem histórico financeiro a preservar - diferente de lançamento.
    @Transactional
    public void delete(UUID id) {
        goalRepository.delete(getOwnedOrThrow(id));
    }

    /**
     * Aporte (amount > 0) ou resgate (amount < 0). O guardado nunca pode ficar negativo.
     *
     * <p>Com {@code accountId}, o dinheiro também se move de verdade: o aporte vira uma
     * transferência dessa conta para a conta da meta, e o resgate, da conta da meta para ela -
     * numa ação só, em vez de guardar na meta e depois lançar a transferência à parte. Sem
     * {@code accountId}, só a meta muda (ex.: resgate gasto direto da conta da meta).
     */
    @Transactional
    public GoalView contribute(UUID goalId, BigDecimal amount, LocalDate date, String note, UUID accountId) {
        Goal goal = getOwnedOrThrow(goalId);
        if (goal.isArchived()) {
            throw new IllegalArgumentException("Meta arquivada não recebe aportes - desarquive primeiro");
        }
        BigDecimal saved = contributionRepository.sumByGoalId(goal.getId());
        if (saved.add(amount).signum() < 0) {
            throw new IllegalArgumentException("Resgate maior que o valor guardado na meta (" + saved.toPlainString() + ")");
        }
        LocalDate day = date != null ? date : today();
        UUID transferId = accountId != null ? transfer(goal, amount, day, accountId) : null;
        contributionRepository.save(new GoalContribution(goal.getId(), amount, day,
                note == null || note.isBlank() ? null : note.strip(), transferId));
        return view(goal);
    }

    @Transactional
    public GoalView removeContribution(UUID goalId, UUID contributionId) {
        Goal goal = getOwnedOrThrow(goalId);
        GoalContribution contribution = contributionRepository.findByIdAndGoalId(contributionId, goal.getId())
                .orElseThrow(() -> new GoalContributionNotFoundException(contributionId));
        BigDecimal saved = contributionRepository.sumByGoalId(goal.getId());
        if (saved.subtract(contribution.getAmount()).signum() < 0) {
            throw new IllegalArgumentException(
                    "Excluir este aporte deixaria a meta com saldo negativo - exclua antes o resgate correspondente");
        }
        contributionRepository.delete(contribution);
        if (contribution.getTransferTransactionId() != null) {
            transactionService.cancel(contribution.getTransferTransactionId(), EditScope.THIS);
        }
        return view(goal);
    }

    private UUID transfer(Goal goal, BigDecimal amount, LocalDate day, UUID accountId) {
        if (goal.getAccountId() == null) {
            throw new IllegalArgumentException("Escolha em que conta o dinheiro da meta fica para movimentar entre contas");
        }
        boolean deposit = amount.signum() > 0;
        UUID source = deposit ? accountId : goal.getAccountId();
        UUID destination = deposit ? goal.getAccountId() : accountId;
        String description = (deposit ? "Guardado na meta " : "Resgatado da meta ") + goal.getName();
        return transactionService.createTransfer(source, destination, amount.abs(), description, day,
                YearMonth.from(day), TransactionStatus.POSTED).getFirst().getId();
    }

    // Mesmo padrão de TransactionService.validateAccount: conta é campo do payload, então
    // inválida/não visível vira 400, não 404.
    private UUID validateAccount(UUID accountId) {
        if (accountId == null) {
            return null;
        }
        try {
            return accountService.get(accountId).getId();
        } catch (AccountNotFoundException e) {
            throw new IllegalArgumentException("accountId inválido ou não visível: " + accountId);
        }
    }

    private GoalView view(Goal goal) {
        List<UUID> ids = List.of(goal.getId());
        BigDecimal saved = contributionRepository.sumByGoalId(goal.getId());
        BigDecimal thisMonth = toMap(contributionRepository.sumByGoalSince(ids, today().withDayOfMonth(1)))
                .getOrDefault(goal.getId(), BigDecimal.ZERO);
        return GoalView.of(goal, saved, thisMonth, today());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static Map<UUID, BigDecimal> toMap(List<GoalAmountProjection> rows) {
        return rows.stream().collect(Collectors.toMap(GoalAmountProjection::getGoalId, GoalAmountProjection::getTotal));
    }

    private Goal getOwnedOrThrow(UUID id) {
        return goalRepository.findByIdAndHouseholdId(id, CurrentUser.householdId())
                .orElseThrow(() -> new GoalNotFoundException(id));
    }
}
