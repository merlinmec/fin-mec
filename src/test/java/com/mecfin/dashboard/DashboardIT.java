package com.mecfin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.account.api.AccountResponse;
import com.mecfin.account.api.CreateAccountRequest;
import com.mecfin.account.domain.AccountType;
import com.mecfin.bill.api.CreateBillRequest;
import com.mecfin.budget.api.CreateBudgetRequest;
import com.mecfin.category.api.CategoryResponse;
import com.mecfin.category.api.CreateCategoryRequest;
import com.mecfin.category.domain.CategoryType;
import com.mecfin.dashboard.api.DashboardResponse;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
import com.mecfin.transaction.api.CreateTransactionRequest;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DashboardIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private RestTestClient client;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private AuthenticatedTestUser registerUser() {
        return AuthTestSupport.registerAndLogin(client, uniqueEmail(), "s3cret1234");
    }

    private UUID createAccount(AuthenticatedTestUser user, BigDecimal initialBalance) {
        return client.post().uri("/accounts")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateAccountRequest("Conta Corrente", AccountType.CHECKING, initialBalance))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AccountResponse.class)
                .returnResult()
                .getResponseBody()
                .id();
    }

    private UUID createCategory(AuthenticatedTestUser user) {
        return client.post().uri("/categories")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCategoryRequest("Lazer", CategoryType.EXPENSE, null, null, null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CategoryResponse.class)
                .returnResult()
                .getResponseBody()
                .id();
    }

    private void createTransaction(
            AuthenticatedTestUser user, UUID accountId, UUID categoryId, TransactionType type, BigDecimal amount,
            TransactionStatus status, LocalDate transactionDate, YearMonth competenceMonth) {
        client.post().uri("/transactions")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateTransactionRequest(
                        accountId, categoryId, type, amount, "Lancamento", transactionDate, competenceMonth, status, null))
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    void dashboardWithoutSession_returns401() {
        client.get().uri("/dashboard")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void dashboardForHouseholdWithoutAnyDataReturnsZeroedSummary() {
        AuthenticatedTestUser user = registerUser();

        DashboardResponse body = user.authenticate(client.get().uri("/dashboard?month=2026-08"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(DashboardResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.referenceMonth()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(body.accountBalances()).isEmpty();
        assertThat(body.totalLedgerBalance()).isEqualByComparingTo("0.00");
        assertThat(body.totalAvailableBalance()).isEqualByComparingTo("0.00");
        assertThat(body.monthlyIncome()).isEqualByComparingTo("0.00");
        assertThat(body.monthlyExpense()).isEqualByComparingTo("0.00");
        assertThat(body.projectedBalance()).isEqualByComparingTo("0.00");
        assertThat(body.upcomingBills()).isEmpty();
        assertThat(body.expensesByCategory()).isEmpty();
        assertThat(body.budgets()).isEmpty();
    }

    @Test
    void dashboardAggregatesBalancesIncomeExpenseBillsAndBudgetsOfOwnHouseholdOnly() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user, new BigDecimal("1000.00"));
        UUID categoryId = createCategory(user);
        YearMonth month = YearMonth.of(2026, 8);

        createTransaction(user, accountId, categoryId, TransactionType.INCOME, new BigDecimal("3000.00"),
                TransactionStatus.POSTED, LocalDate.of(2026, 8, 5), month);
        createTransaction(user, accountId, categoryId, TransactionType.EXPENSE, new BigDecimal("400.00"),
                TransactionStatus.POSTED, LocalDate.of(2026, 8, 10), month);
        // não conta pro disponível hoje (data futura), mas conta pro saldo contábil.
        createTransaction(user, accountId, categoryId, TransactionType.EXPENSE, new BigDecimal("100.00"),
                TransactionStatus.POSTED, LocalDate.now().plusDays(30), YearMonth.from(LocalDate.now().plusDays(30)));
        // não conta em nada - PENDING.
        createTransaction(user, accountId, categoryId, TransactionType.EXPENSE, new BigDecimal("999.00"),
                TransactionStatus.PENDING, LocalDate.of(2026, 8, 12), month);

        client.post().uri("/bills")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateBillRequest("Aluguel", new BigDecimal("1200.00"), LocalDate.of(2026, 8, 25), null, null, null))
                .exchange()
                .expectStatus().isCreated();

        client.post().uri("/budgets")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateBudgetRequest(categoryId, month, new BigDecimal("500.00")))
                .exchange()
                .expectStatus().isCreated();

        // ruído de outro household - nada disso deve aparecer no dashboard do primeiro.
        AuthenticatedTestUser other = registerUser();
        UUID otherAccountId = createAccount(other, new BigDecimal("9999.00"));
        UUID otherCategoryId = createCategory(other);
        createTransaction(other, otherAccountId, otherCategoryId, TransactionType.INCOME, new BigDecimal("9999.00"),
                TransactionStatus.POSTED, LocalDate.of(2026, 8, 1), month);

        DashboardResponse body = user.authenticate(client.get().uri("/dashboard?month=2026-08"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(DashboardResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.accountBalances()).hasSize(1);
        // saldo contabil: 1000 (inicial) + 3000 (income) - 400 - 100 (as duas expenses POSTED, incluindo a futura)
        assertThat(body.accountBalances().get(0).ledgerBalance()).isEqualByComparingTo("3500.00");
        // saldo disponivel: mesma coisa, mas sem a expense de data futura
        assertThat(body.accountBalances().get(0).availableBalance()).isEqualByComparingTo("3600.00");
        assertThat(body.totalLedgerBalance()).isEqualByComparingTo("3500.00");
        assertThat(body.totalAvailableBalance()).isEqualByComparingTo("3600.00");

        assertThat(body.monthlyIncome()).isEqualByComparingTo("3000.00");
        assertThat(body.monthlyExpense()).isEqualByComparingTo("400.00");

        assertThat(body.upcomingBills()).hasSize(1);
        assertThat(body.upcomingBills().get(0).description()).isEqualTo("Aluguel");

        // previsao: saldo disponivel (3600) - bill OPEN vencendo ate o fim do mes (1200)
        assertThat(body.projectedBalance()).isEqualByComparingTo("2400.00");

        assertThat(body.expensesByCategory()).hasSize(1);
        assertThat(body.expensesByCategory().get(0).amount()).isEqualByComparingTo("400.00");
        assertThat(body.expensesByCategory().get(0).categoryName()).isEqualTo("Lazer");

        assertThat(body.budgets()).hasSize(1);
        assertThat(body.budgets().get(0).amount()).isEqualByComparingTo("500.00");
        assertThat(body.budgets().get(0).spent()).isEqualByComparingTo("400.00");
    }

    @Test
    void dashboardWithoutMonthParameterDefaultsToCurrentMonth() {
        AuthenticatedTestUser user = registerUser();

        DashboardResponse body = user.authenticate(client.get().uri("/dashboard"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(DashboardResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.referenceMonth()).isEqualTo(YearMonth.now());
    }

    @Test
    void dashboardWithInvalidMonth_returns400() {
        AuthenticatedTestUser user = registerUser();

        user.authenticate(client.get().uri("/dashboard?month=not-a-month"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
