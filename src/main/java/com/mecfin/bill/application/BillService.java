package com.mecfin.bill.application;

import com.mecfin.account.application.AccountNotFoundException;
import com.mecfin.account.application.AccountService;
import com.mecfin.bill.domain.Bill;
import com.mecfin.bill.domain.BillStatus;
import com.mecfin.bill.infra.BillRepository;
import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.shared.domain.RecurrenceRule;
import com.mecfin.shared.security.CurrentUser;
import com.mecfin.transaction.application.TransactionService;
import com.mecfin.transaction.domain.Transaction;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillService {

    private final BillRepository billRepository;
    private final CategoryRepository categoryRepository;
    private final AccountService accountService;
    private final TransactionService transactionService;

    public BillService(
            BillRepository billRepository,
            CategoryRepository categoryRepository,
            AccountService accountService,
            TransactionService transactionService) {
        this.billRepository = billRepository;
        this.categoryRepository = categoryRepository;
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    @Transactional
    public BillView create(
            String description,
            BigDecimal amount,
            LocalDate dueDate,
            UUID sourceAccountId,
            UUID categoryId,
            RecurrenceRule recurrenceRule) {
        UUID householdId = CurrentUser.householdId();
        validateAccount(sourceAccountId);
        validateCategory(categoryId, householdId);
        Bill bill = new Bill(householdId, description, amount, dueDate, sourceAccountId, categoryId, recurrenceRule);
        return new BillView(billRepository.save(bill));
    }

    // statusFilter == OVERDUE é tratado à parte: nunca é o valor persistido em status, então
    // vira "OPEN + due_date no passado" na consulta (ver BillRepository).
    public List<BillView> list(BillStatus statusFilter) {
        UUID householdId = CurrentUser.householdId();
        List<Bill> bills;
        if (statusFilter == null) {
            bills = billRepository.findAllByHouseholdIdOrderByDueDateAsc(householdId);
        } else if (statusFilter == BillStatus.OVERDUE) {
            bills = billRepository.findAllByHouseholdIdAndStatusAndDueDateBeforeOrderByDueDateAsc(
                    householdId, BillStatus.OPEN, LocalDate.now());
        } else {
            bills = billRepository.findAllByHouseholdIdAndStatusOrderByDueDateAsc(householdId, statusFilter);
        }
        return bills.stream().map(BillView::new).toList();
    }

    public BillView get(UUID id) {
        return new BillView(getOwnedOrThrow(id));
    }

    @Transactional
    public BillView update(
            UUID id,
            String description,
            BigDecimal amount,
            LocalDate dueDate,
            UUID sourceAccountId,
            UUID categoryId,
            RecurrenceRule recurrenceRule) {
        Bill bill = getOwnedOrThrow(id);
        requireOpen(bill);
        validateAccount(sourceAccountId);
        validateCategory(categoryId, bill.getHouseholdId());
        bill.update(description, amount, dueDate, sourceAccountId, categoryId, recurrenceRule);
        return new BillView(bill);
    }

    // Baixa: registra o pagamento como uma Transaction real (chamada direta a
    // TransactionService, mesmo padrão já previsto no roadmap - "BillService chama
    // TransactionService ao registrar pagamento") e liga o id de volta em paid_transaction_id.
    // accountId explícito tem prioridade sobre o sourceAccountId padrão do bill; a validação
    // final de conta/categoria acontece dentro do próprio TransactionService.create (cobre o
    // caso de sourceAccountId ter sido válido na criação do bill mas excluído depois).
    @Transactional
    public BillView pay(UUID id, UUID accountId, LocalDate paymentDate, BigDecimal paidAmount) {
        Bill bill = getOwnedOrThrow(id);
        requireOpen(bill);
        UUID payingAccountId = accountId != null ? accountId : bill.getSourceAccountId();
        if (payingAccountId == null) {
            throw new IllegalArgumentException(
                    "Informe a conta de pagamento - esta conta a pagar não tem uma conta de origem padrão");
        }
        BigDecimal amount = paidAmount != null ? paidAmount : bill.getAmount();
        Transaction transaction = transactionService.create(
                payingAccountId, bill.getCategoryId(), TransactionType.EXPENSE, amount, bill.getDescription(),
                paymentDate, YearMonth.from(paymentDate), TransactionStatus.POSTED, null);
        bill.pay(transaction.getId());
        return new BillView(bill);
    }

    @Transactional
    public void cancel(UUID id) {
        Bill bill = getOwnedOrThrow(id);
        requireOpen(bill);
        bill.cancel();
    }

    private Bill getOwnedOrThrow(UUID id) {
        return billRepository.findByIdAndHouseholdId(id, CurrentUser.householdId())
                .orElseThrow(() -> new BillNotFoundException(id));
    }

    private void requireOpen(Bill bill) {
        if (!bill.isOpen()) {
            throw new BillNotOpenException(bill.getId(), bill.getStatus());
        }
    }

    // sourceAccountId é campo de payload (não o recurso primário do endpoint) - inválido/não
    // visível vira 400, não 404, mesmo padrão de CategoryService.validateParent.
    private void validateAccount(UUID accountId) {
        if (accountId == null) {
            return;
        }
        try {
            accountService.get(accountId);
        } catch (AccountNotFoundException e) {
            throw new IllegalArgumentException("sourceAccountId inválido ou não visível: " + accountId);
        }
    }

    private void validateCategory(UUID categoryId, UUID householdId) {
        if (categoryId == null) {
            return;
        }
        categoryRepository.findVisibleByIdAndHouseholdId(categoryId, householdId)
                .orElseThrow(() -> new IllegalArgumentException("categoryId inválido ou não visível: " + categoryId));
    }
}
