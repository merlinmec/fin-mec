package com.mecfin.demo.application;

import com.mecfin.account.application.AccountService;
import com.mecfin.account.domain.Account;
import com.mecfin.account.domain.AccountType;
import com.mecfin.bill.application.BillService;
import com.mecfin.budget.application.BudgetService;
import com.mecfin.category.application.CategoryService;
import com.mecfin.category.domain.Category;
import com.mecfin.category.domain.CategoryType;
import com.mecfin.creditcard.application.CreditCardService;
import com.mecfin.creditcard.domain.CreditCard;
import com.mecfin.goal.application.GoalService;
import com.mecfin.goal.application.GoalView;
import com.mecfin.shared.domain.RecurrenceRule;
import com.mecfin.transaction.application.TransactionService;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seis meses de vida financeira fictícia para a conta de visitante, gravados pelos serviços de
 * aplicação de cada módulo, como o usuário faria pela tela: as mesmas validações e regras valem
 * (fatura aberta, parcelas, transferência em par...). Tudo é relativo a hoje, então a demonstração
 * nunca envelhece: o mês atual tem o que já aconteceu efetivado e o resto como previsto.
 *
 * <p>Roda com o visitante já autenticado na requisição: todo serviço escopa pelo household dele.
 * Uma transação só para tudo: no plano gratuito (0,1 CPU) cada commit pesa, e se algo falhar no
 * meio não sobra uma conta pela metade.
 */
@Component
public class DemoDataSeeder {

    private static final int MONTHS_OF_HISTORY = 6;

    private final AccountService accounts;
    private final CategoryService categories;
    private final TransactionService transactions;
    private final BillService bills;
    private final BudgetService budgets;
    private final CreditCardService creditCards;
    private final GoalService goals;
    private final Clock clock;

    public DemoDataSeeder(AccountService accounts, CategoryService categories, TransactionService transactions,
            BillService bills, BudgetService budgets, CreditCardService creditCards, GoalService goals,
            Clock clock) {
        this.accounts = accounts;
        this.categories = categories;
        this.transactions = transactions;
        this.bills = bills;
        this.budgets = budgets;
        this.creditCards = creditCards;
        this.goals = goals;
        this.clock = clock;
    }

    @Transactional
    public void seed() {
        LocalDate today = LocalDate.now(clock);
        YearMonth current = YearMonth.from(today);

        // As categorias padrão do sistema são só de despesa; as demais o usuário cria.
        UUID salary = categories.create("Salário", CategoryType.INCOME, null, "#10B981", "briefcase").getId();
        UUID freelance = categories.create("Freelance", CategoryType.INCOME, null, "#14B8A6", "banknote").getId();
        UUID dining = categories.create("Restaurantes", CategoryType.EXPENSE, null, "#F97316", "utensils").getId();
        UUID utilities = categories.create("Contas da casa", CategoryType.EXPENSE, null, "#06B6D4", "receipt").getId();
        List<Category> all = categories.list();
        UUID housing = expense(all, "Moradia");
        UUID food = expense(all, "Alimentação");
        UUID transport = expense(all, "Transporte");
        UUID health = expense(all, "Saúde");
        UUID education = expense(all, "Educação");
        UUID leisure = expense(all, "Lazer");

        Account checking = accounts.create("Nubank", AccountType.CHECKING, money(3200));
        Account savings = accounts.create("Poupança", AccountType.SAVINGS, money(8000));
        Account wallet = accounts.create("Carteira", AccountType.WALLET, money(450));

        for (int i = 0; i < MONTHS_OF_HISTORY; i++) {
            YearMonth month = current.minusMonths(MONTHS_OF_HISTORY - 1 - i);
            Month m = new Month(month, today);
            m.entry(checking, salary, TransactionType.INCOME, money(6800), "Salário", 5);
            if (i % 2 == 1) {
                m.entry(checking, freelance, TransactionType.INCOME, vary(1350, i, 500), "Projeto freelance", 8);
            }
            m.entry(checking, housing, TransactionType.EXPENSE, money(1900), "Aluguel", 10);
            m.entry(checking, food, TransactionType.EXPENSE, vary(412, i, 120), "Mercado do mês", 3);
            m.entry(checking, food, TransactionType.EXPENSE, vary(365, i, 140), "Feira e hortifruti", 17);
            m.entry(wallet, food, TransactionType.EXPENSE, vary(42, i, 20), "Padaria", 9);
            m.entry(checking, transport, TransactionType.EXPENSE, vary(118, i, 40), "Uber", 7);
            m.entry(checking, transport, TransactionType.EXPENSE, vary(240, i, 160), "Combustível", 14);
            m.entry(checking, dining, TransactionType.EXPENSE, vary(214, i, 90), "Jantar fora", 20);
            m.entry(checking, leisure, TransactionType.EXPENSE, vary(160, i, 120), "Cinema e passeio", 23);
            m.entry(checking, utilities, TransactionType.EXPENSE, money(119.90), "Internet fibra", 15);
            m.entry(checking, utilities, TransactionType.EXPENSE, vary(178, i, 60), "Energia elétrica", 18);
            if (i == 2) {
                m.entry(checking, health, TransactionType.EXPENSE, money(640), "Consulta e exames", 12);
            }
            if (i == 4) {
                m.entry(checking, education, TransactionType.EXPENSE, money(890), "Curso de inglês", 2);
            }
            if (i == 1) {
                m.transfer(checking, wallet, money(300), "Saque", 2);
            }
            m.transfer(checking, savings, money(800), "Reserva do mês", 6);
        }

        // Lançamento fixo: as próximas ocorrências entram como previstas.
        LocalDate streamingDay = new Month(current, today).date(18);
        transactions.create(checking.getId(), leisure, TransactionType.EXPENSE, money(39.90), "Streaming",
                streamingDay, current, statusFor(streamingDay, today), RecurrenceRule.MONTHLY);

        // Contas a pagar: uma vencida (status calculado na leitura) e as próximas.
        bills.create("IPVA - 3ª parcela", money(412.35), today.minusDays(4), checking.getId(), transport, null);
        bills.create("Condomínio", money(540), today.plusDays(10), checking.getId(), housing, RecurrenceRule.MONTHLY);
        bills.create("Energia elétrica", money(183.70), today.plusDays(12), checking.getId(), utilities, null);

        budgets.create(food, current, money(1500));
        budgets.create(transport, current, money(450));
        budgets.create(leisure, current, money(500));
        budgets.create(dining, current, money(350));
        budgets.create(health, current, money(300));

        // Compras datadas de hoje: caem sempre na fatura aberta, qualquer que seja o dia do mês.
        // Com parcelas, o valor informado é o de cada parcela.
        CreditCard card = creditCards.create("Nubank Ultravioleta", money(9000), 3, 10, checking.getId());
        creditCards.registerCharge(card.getId(), education, TransactionType.EXPENSE, money(420), "Notebook",
                today, 10);
        creditCards.registerCharge(card.getId(), leisure, TransactionType.EXPENSE, money(460), "Passagem aérea",
                today, 3);
        creditCards.registerCharge(card.getId(), dining, TransactionType.EXPENSE, money(186.50),
                "Restaurante japonês", today, null);
        creditCards.registerCharge(card.getId(), health, TransactionType.EXPENSE, money(74.30), "Farmácia",
                today, null);

        GoalView reserve = goals.create("Reserva de emergência", money(24000), today.plusMonths(8), "#10B981",
                "piggy-bank", null);
        goals.contribute(reserve.goal().getId(), money(1500), today.minusDays(120), null, null);
        goals.contribute(reserve.goal().getId(), money(1800), today.minusDays(60), null, null);
        goals.contribute(reserve.goal().getId(), money(1200), today.minusDays(2), null, null);
        GoalView trip = goals.create("Viagem para o Chile", money(9000), today.plusMonths(5), "#3B82F6", "plane", null);
        goals.contribute(trip.goal().getId(), money(900), today.minusDays(90), null, null);
        goals.contribute(trip.goal().getId(), money(1100), today.minusDays(25), null, null);
    }

    /** Um mês do histórico: o que cai depois de hoje entra como previsto. */
    private final class Month {
        private final YearMonth month;
        private final LocalDate today;

        Month(YearMonth month, LocalDate today) {
            this.month = month;
            this.today = today;
        }

        LocalDate date(int day) {
            return month.atDay(Math.min(day, month.lengthOfMonth()));
        }

        void entry(Account account, UUID categoryId, TransactionType type, BigDecimal amount, String description,
                int day) {
            LocalDate date = date(day);
            transactions.create(account.getId(), categoryId, type, amount, description, date, month,
                    statusFor(date, today), null);
        }

        void transfer(Account from, Account to, BigDecimal amount, String description, int day) {
            LocalDate date = date(day);
            transactions.createTransfer(from.getId(), to.getId(), amount, description, date, month,
                    statusFor(date, today));
        }
    }

    private static TransactionStatus statusFor(LocalDate date, LocalDate today) {
        return date.isAfter(today) ? TransactionStatus.PENDING : TransactionStatus.POSTED;
    }

    private static UUID expense(List<Category> all, String name) {
        return all.stream()
                .filter(c -> c.getType() == CategoryType.EXPENSE)
                .filter(c -> c.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)))
                .map(Category::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Categoria padrão ausente: " + name));
    }

    /** Valor que muda um pouco de mês a mês (determinístico), para os gráficos não ficarem retos. */
    private static BigDecimal vary(double base, int monthIndex, double spread) {
        double value = base + ((monthIndex * 37) % spread) - spread / 2;
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
