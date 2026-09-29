package com.mecfin.insight.application;

import com.mecfin.account.application.AccountService;
import com.mecfin.account.domain.Account;
import com.mecfin.bill.domain.Bill;
import com.mecfin.bill.domain.BillStatus;
import com.mecfin.bill.infra.BillRepository;
import com.mecfin.creditcard.application.CreditCardInvoiceView;
import com.mecfin.creditcard.application.CreditCardService;
import com.mecfin.creditcard.domain.CreditCard;
import com.mecfin.creditcard.domain.CreditCardInvoiceStatus;
import com.mecfin.insight.application.BalanceForecast.Event;
import com.mecfin.insight.application.BalanceForecast.EventKind;
import com.mecfin.insight.application.BalanceForecast.Point;
import com.mecfin.shared.security.CurrentUser;
import com.mecfin.transaction.domain.Transaction;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import com.mecfin.transaction.infra.AccountBalanceProjection;
import com.mecfin.transaction.infra.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Previsão de saldo (Fase 19). Derivada na leitura, nunca persistida. */
@Service
public class ForecastService {

    public static final int MIN_DAYS = 7;
    public static final int MAX_DAYS = 180;

    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final BillRepository billRepository;
    private final CreditCardService creditCardService;
    private final Clock clock;

    public ForecastService(AccountService accountService, TransactionRepository transactionRepository,
            BillRepository billRepository, CreditCardService creditCardService, Clock clock) {
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
        this.billRepository = billRepository;
        this.creditCardService = creditCardService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BalanceForecast forecast(int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new IllegalArgumentException("A previsão vai de " + MIN_DAYS + " a " + MAX_DAYS + " dias");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate until = today.plusDays(days);
        List<Account> accounts = accountService.list();
        List<UUID> accountIds = accounts.stream().map(Account::getId).toList();

        BigDecimal start = accounts.stream().map(Account::getInitialBalance).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(transactionRepository.sumSignedAmountsByAccount(accountIds, TransactionStatus.POSTED, today)
                        .stream().map(AccountBalanceProjection::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add));

        List<Event> events = new ArrayList<>();
        if (!accountIds.isEmpty()) {
            for (Transaction t : transactionRepository.findScheduledUntil(accountIds, today, until)) {
                BigDecimal signed = t.getType() == TransactionType.INCOME ? t.getAmount() : t.getAmount().negate();
                events.add(event(today, t.getTransactionDate(), EventKind.TRANSACTION, t.getDescription(), signed));
            }
        }
        for (Bill bill : billRepository.findAllByHouseholdIdAndStatusOrderByDueDateAsc(CurrentUser.householdId(),
                BillStatus.OPEN)) {
            if (!bill.getDueDate().isAfter(until)) {
                events.add(event(today, bill.getDueDate(), EventKind.BILL, bill.getDescription(),
                        bill.getAmount().negate()));
            }
        }
        for (CreditCard card : creditCardService.list()) {
            for (CreditCardInvoiceView invoice : creditCardService.listInvoices(card.getId())) {
                BigDecimal total = invoice.totalAmount();
                if (invoice.invoice().getStatus() != CreditCardInvoiceStatus.PAID && total.signum() > 0
                        && !invoice.invoice().getDueDate().isAfter(until)) {
                    events.add(event(today, invoice.invoice().getDueDate(), EventKind.CREDIT_CARD_INVOICE,
                            "Fatura " + card.getName(), total.negate()));
                }
            }
        }
        return build(today, until, start, events);
    }

    /** Monta a curva. Pacote-privado e puro: é o que os testes de unidade exercitam. */
    static BalanceForecast build(LocalDate today, LocalDate until, BigDecimal start, List<Event> events) {
        Map<LocalDate, List<Event>> byDay = new TreeMap<>();
        for (Event e : events) {
            byDay.computeIfAbsent(e.date(), d -> new ArrayList<>()).add(e);
        }
        List<Point> points = new ArrayList<>();
        BigDecimal balance = start;
        BigDecimal lowest = start;
        LocalDate lowestDate = today;
        LocalDate firstNegative = start.signum() < 0 ? today : null;
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        for (LocalDate day = today; !day.isAfter(until); day = day.plusDays(1)) {
            List<Event> dayEvents = byDay.getOrDefault(day, List.of()).stream()
                    .sorted(Comparator.comparing(Event::amount))
                    .toList();
            BigDecimal income = BigDecimal.ZERO;
            BigDecimal expense = BigDecimal.ZERO;
            for (Event e : dayEvents) {
                if (e.amount().signum() >= 0) {
                    income = income.add(e.amount());
                } else {
                    expense = expense.add(e.amount().negate());
                }
            }
            balance = balance.add(income).subtract(expense);
            totalIncome = totalIncome.add(income);
            totalExpense = totalExpense.add(expense);
            if (balance.compareTo(lowest) < 0) {
                lowest = balance;
                lowestDate = day;
            }
            if (firstNegative == null && balance.signum() < 0) {
                firstNegative = day;
            }
            points.add(new Point(day, balance, income, expense, dayEvents));
        }
        return new BalanceForecast(today, until, start, balance, lowest, lowestDate, firstNegative, totalIncome,
                totalExpense, points);
    }

    // Atrasado (data no passado) conta hoje: o dinheiro ainda não saiu, mas vai sair.
    private static Event event(LocalDate today, LocalDate date, EventKind kind, String description, BigDecimal amount) {
        boolean overdue = date.isBefore(today);
        return new Event(overdue ? today : date, kind, description, amount, overdue);
    }
}
