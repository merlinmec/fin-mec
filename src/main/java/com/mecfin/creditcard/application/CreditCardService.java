package com.mecfin.creditcard.application;

import com.mecfin.account.application.AccountNotFoundException;
import com.mecfin.account.application.AccountService;
import com.mecfin.category.infra.CategoryRepository;
import com.mecfin.creditcard.domain.CreditCard;
import com.mecfin.creditcard.domain.CreditCardCharge;
import com.mecfin.creditcard.domain.CreditCardInvoice;
import com.mecfin.creditcard.domain.CreditCardInvoiceStatus;
import com.mecfin.creditcard.infra.CreditCardChargeRepository;
import com.mecfin.creditcard.infra.CreditCardInvoiceRepository;
import com.mecfin.creditcard.infra.CreditCardRepository;
import com.mecfin.shared.security.CurrentUser;
import com.mecfin.transaction.application.TransactionService;
import com.mecfin.transaction.domain.Transaction;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditCardService {

    private final CreditCardRepository creditCardRepository;
    private final CreditCardInvoiceRepository invoiceRepository;
    private final CreditCardChargeRepository chargeRepository;
    private final CategoryRepository categoryRepository;
    private final AccountService accountService;
    private final TransactionService transactionService;

    public CreditCardService(
            CreditCardRepository creditCardRepository,
            CreditCardInvoiceRepository invoiceRepository,
            CreditCardChargeRepository chargeRepository,
            CategoryRepository categoryRepository,
            AccountService accountService,
            TransactionService transactionService) {
        this.creditCardRepository = creditCardRepository;
        this.invoiceRepository = invoiceRepository;
        this.chargeRepository = chargeRepository;
        this.categoryRepository = categoryRepository;
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    @Transactional
    public CreditCard create(
            String name, BigDecimal creditLimit, int closingDay, int dueDay, UUID paymentAccountId) {
        validateAccount(paymentAccountId);
        CreditCard card = new CreditCard(
                CurrentUser.householdId(), name, creditLimit, closingDay, dueDay, paymentAccountId);
        return creditCardRepository.save(card);
    }

    public List<CreditCard> list() {
        return creditCardRepository.findAllByHouseholdIdAndDeletedAtIsNullOrderByNameAsc(CurrentUser.householdId());
    }

    public CreditCard get(UUID id) {
        return creditCardRepository.findByIdAndHouseholdIdAndDeletedAtIsNull(id, CurrentUser.householdId())
                .orElseThrow(() -> new CreditCardNotFoundException(id));
    }

    @Transactional
    public CreditCard update(
            UUID id, String name, BigDecimal creditLimit, int closingDay, int dueDay, UUID paymentAccountId,
            boolean archived) {
        CreditCard card = get(id);
        validateAccount(paymentAccountId);
        card.update(name, creditLimit, closingDay, dueDay, paymentAccountId, archived);
        return card;
    }

    @Transactional
    public void delete(UUID id) {
        get(id).softDelete();
    }

    // Registra uma cobrança (compra ou estorno) no cartão. Sem installments (ou < 2), cria uma
    // única cobrança na fatura resolvida por purchaseDate; com installments, materializa todas
    // as N parcelas de uma vez (quantidade fixa e conhecida na criação, mesmo espírito de
    // TransactionService.createInstallments) - cada parcela pode cair numa fatura diferente,
    // por isso resolveOrCreateInvoice roda de novo a cada iteração.
    @Transactional
    public List<CreditCardCharge> registerCharge(
            UUID cardId,
            UUID categoryId,
            TransactionType type,
            BigDecimal amountPerInstallment,
            String description,
            LocalDate purchaseDate,
            Integer installments) {
        if (type == TransactionType.TRANSFER) {
            throw new IllegalArgumentException("Transferência não se aplica a cobrança de cartão");
        }
        CreditCard card = get(cardId);
        validateCategory(categoryId);

        if (installments != null && installments < 2) {
            throw new IllegalArgumentException("installments deve ser >= 2 (omita para uma cobrança avulsa)");
        }
        int total = installments != null ? installments : 1;

        List<CreditCardCharge> charges = new ArrayList<>();
        UUID groupId = total >= 2 ? UUID.randomUUID() : null;
        for (int i = 1; i <= total; i++) {
            LocalDate legPurchaseDate = purchaseDate.plusMonths(i - 1L);
            CreditCardInvoice invoice = resolveOrCreateInvoice(card, legPurchaseDate);
            requireOpen(invoice);
            CreditCardCharge charge = total >= 2
                    ? CreditCardCharge.installmentLeg(
                            invoice.getId(), categoryId, type, amountPerInstallment,
                            description + " (" + i + "/" + total + ")", legPurchaseDate, i, total, groupId)
                    : CreditCardCharge.of(
                            invoice.getId(), categoryId, type, amountPerInstallment, description, legPurchaseDate);
            charges.add(chargeRepository.save(charge));
        }
        return charges;
    }

    public List<CreditCardInvoiceView> listInvoices(UUID cardId) {
        get(cardId);
        return invoiceRepository.findAllByCreditCardIdOrderByReferenceMonthDesc(cardId).stream()
                .map(this::toView)
                .toList();
    }

    public CreditCardInvoiceView getInvoice(UUID id) {
        return toView(getOwnedInvoiceOrThrow(id));
    }

    @Transactional
    public void deleteCharge(UUID invoiceId, UUID chargeId) {
        CreditCardInvoice invoice = getOwnedInvoiceOrThrow(invoiceId);
        requireOpen(invoice);
        CreditCardCharge charge = chargeRepository.findByIdAndCreditCardInvoiceId(chargeId, invoiceId)
                .orElseThrow(() -> new CreditCardChargeNotFoundException(chargeId));
        chargeRepository.delete(charge);
    }

    // Baixa: soma as cobranças e cria UMA Transaction real pro total (chamada direta a
    // TransactionService, mesmo padrão de BillService.pay), sem categoria - cada cobrança já
    // contou pra sua própria categoria no momento em que foi registrada; repetir a categoria
    // aqui duplicaria o gasto se este módulo um dia alimentar relatórios por categoria.
    @Transactional
    public CreditCardInvoiceView payInvoice(UUID id, UUID paymentAccountId, LocalDate paymentDate, BigDecimal paidAmount) {
        CreditCardInvoice invoice = getOwnedInvoiceOrThrow(id);
        requireOpen(invoice);
        CreditCard card = creditCardRepository.findById(invoice.getCreditCardId())
                .orElseThrow(() -> new CreditCardNotFoundException(invoice.getCreditCardId()));

        UUID payingAccountId = paymentAccountId != null ? paymentAccountId : card.getPaymentAccountId();
        if (payingAccountId == null) {
            throw new IllegalArgumentException(
                    "Informe a conta de pagamento - este cartão não tem uma conta de pagamento padrão");
        }
        List<CreditCardCharge> charges = chargeRepository.findAllByCreditCardInvoiceIdOrderByPurchaseDateAsc(id);
        BigDecimal total = new CreditCardInvoiceView(invoice, charges).totalAmount();
        BigDecimal amount = paidAmount != null ? paidAmount : total;

        Transaction transaction = transactionService.create(
                payingAccountId, null, TransactionType.EXPENSE, amount,
                "Fatura " + card.getName() + " - " + invoice.getReferenceMonth(), paymentDate,
                YearMonth.from(paymentDate), TransactionStatus.POSTED, null);
        invoice.pay(transaction.getId());
        return new CreditCardInvoiceView(invoice, charges);
    }

    // Regra de resolução: fecha no closingDay do mês da compra (ou do mês seguinte, se a compra
    // já passou do closingDay); a fatura vence sempre no mês seguinte ao fechamento (dueDay
    // desse mês) e é identificada (referenceMonth) pelo mês de vencimento - simplificação
    // deliberada (não modela closingDay/dueDay cruzando de forma diferente por cartão), mesmo
    // espírito de outras simplificações do projeto (ex.: recorrência só metadado).
    private CreditCardInvoice resolveOrCreateInvoice(CreditCard card, LocalDate purchaseDate) {
        YearMonth closingMonth = purchaseDate.getDayOfMonth() <= card.getClosingDay()
                ? YearMonth.from(purchaseDate)
                : YearMonth.from(purchaseDate).plusMonths(1);
        LocalDate closingDate = clampDay(closingMonth, card.getClosingDay());
        YearMonth referenceMonth = closingMonth.plusMonths(1);
        LocalDate dueDate = clampDay(referenceMonth, card.getDueDay());

        return invoiceRepository.findByCreditCardIdAndReferenceMonth(card.getId(), referenceMonth.atDay(1))
                .orElseGet(() -> invoiceRepository.save(
                        new CreditCardInvoice(card.getId(), referenceMonth, closingDate, dueDate)));
    }

    private static LocalDate clampDay(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }

    private CreditCardInvoiceView toView(CreditCardInvoice invoice) {
        return new CreditCardInvoiceView(
                invoice, chargeRepository.findAllByCreditCardInvoiceIdOrderByPurchaseDateAsc(invoice.getId()));
    }

    // household_id não existe em credit_card_invoices - resolvido via credit_card_id, cujo
    // cartão pertence ao household. Inclui cartão soft-deleted de propósito, assim como
    // AccountService.householdAccountIds() faz para não perder histórico de fatura de um
    // cartão já excluído.
    private CreditCardInvoice getOwnedInvoiceOrThrow(UUID id) {
        List<UUID> cardIds = creditCardRepository.findAllByHouseholdId(CurrentUser.householdId()).stream()
                .map(CreditCard::getId)
                .toList();
        return invoiceRepository.findByIdAndCreditCardIdIn(id, cardIds)
                .orElseThrow(() -> new CreditCardInvoiceNotFoundException(id));
    }

    private void requireOpen(CreditCardInvoice invoice) {
        CreditCardInvoiceView view = new CreditCardInvoiceView(invoice, List.of());
        if (view.effectiveStatus() != CreditCardInvoiceStatus.OPEN) {
            throw new CreditCardInvoiceNotOpenException(invoice.getId(), view.effectiveStatus());
        }
    }

    // paymentAccountId é campo de payload (não o recurso primário do endpoint) - inválido/não
    // visível vira 400, não 404, mesmo padrão de BillService.validateAccount.
    private void validateAccount(UUID accountId) {
        if (accountId == null) {
            return;
        }
        try {
            accountService.get(accountId);
        } catch (AccountNotFoundException e) {
            throw new IllegalArgumentException("paymentAccountId inválido ou não visível: " + accountId);
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
