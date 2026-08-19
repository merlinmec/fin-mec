package com.mecfin.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.account.api.AccountResponse;
import com.mecfin.account.api.CreateAccountRequest;
import com.mecfin.account.domain.AccountType;
import com.mecfin.budget.api.BudgetResponse;
import com.mecfin.budget.api.CreateBudgetRequest;
import com.mecfin.budget.api.UpdateBudgetRequest;
import com.mecfin.category.api.CategoryResponse;
import com.mecfin.category.api.CreateCategoryRequest;
import com.mecfin.category.domain.CategoryType;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
import com.mecfin.transaction.api.CreateTransactionRequest;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BudgetIT {

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

    private UUID createAccount(AuthenticatedTestUser user) {
        return client.post().uri("/accounts")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateAccountRequest("Conta Corrente", AccountType.CHECKING, new BigDecimal("100.00")))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AccountResponse.class)
                .returnResult()
                .getResponseBody()
                .id();
    }

    private UUID createCategory(AuthenticatedTestUser user, String name) {
        return client.post().uri("/categories")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCategoryRequest(name, CategoryType.EXPENSE, null, null, null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CategoryResponse.class)
                .returnResult()
                .getResponseBody()
                .id();
    }

    private void createTransaction(
            AuthenticatedTestUser user, UUID accountId, UUID categoryId, BigDecimal amount,
            TransactionType type, TransactionStatus status, YearMonth competenceMonth) {
        client.post().uri("/transactions")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateTransactionRequest(
                        accountId, categoryId, type, amount, "Lancamento", LocalDate.now(), competenceMonth, status,
                        null))
                .exchange()
                .expectStatus().isCreated();
    }

    private EntityExchangeResult<BudgetResponse> createBudget(
            AuthenticatedTestUser user, UUID categoryId, YearMonth referenceMonth, BigDecimal amount) {
        return client.post().uri("/budgets")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateBudgetRequest(categoryId, referenceMonth, amount))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(BudgetResponse.class)
                .returnResult();
    }

    @Test
    void createBudget_returns201ScopedToOwnHousehold() {
        AuthenticatedTestUser user = registerUser();
        UUID categoryId = createCategory(user, "Lazer");

        EntityExchangeResult<BudgetResponse> result = createBudget(
                user, categoryId, YearMonth.of(2026, 8), new BigDecimal("500.00"));

        BudgetResponse body = result.getResponseBody();
        assertThat(body.categoryId()).isEqualTo(categoryId);
        assertThat(body.referenceMonth()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(body.amount()).isEqualByComparingTo("500.00");
        assertThat(body.spent()).isEqualByComparingTo("0.00");
        assertThat(body.percentageUsed()).isEqualByComparingTo("0.0000");
    }

    @Test
    void createBudgetWithoutSession_returns401() {
        client.post().uri("/budgets")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateBudgetRequest(UUID.randomUUID(), YearMonth.now(), BigDecimal.TEN))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void createBudgetWithInvisibleCategory_returns400() {
        AuthenticatedTestUser user = registerUser();

        client.post().uri("/budgets")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateBudgetRequest(UUID.randomUUID(), YearMonth.now(), BigDecimal.TEN))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void createDuplicateBudgetForSameCategoryAndMonth_returns409() {
        AuthenticatedTestUser user = registerUser();
        UUID categoryId = createCategory(user, "Lazer");
        createBudget(user, categoryId, YearMonth.of(2026, 8), new BigDecimal("500.00"));

        client.post().uri("/budgets")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateBudgetRequest(categoryId, YearMonth.of(2026, 8), new BigDecimal("300.00")))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void listBudgets_returnsOnlyOwnHouseholdAndFiltersByMonth() {
        AuthenticatedTestUser owner = registerUser();
        UUID ownCategory = createCategory(owner, "Lazer");
        createBudget(owner, ownCategory, YearMonth.of(2026, 8), new BigDecimal("500.00"));
        createBudget(owner, ownCategory, YearMonth.of(2026, 9), new BigDecimal("500.00"));
        AuthenticatedTestUser other = registerUser();
        UUID otherCategory = createCategory(other, "Lazer");
        createBudget(other, otherCategory, YearMonth.of(2026, 8), new BigDecimal("999.00"));

        List<BudgetResponse> result = owner.authenticate(
                        client.get().uri("/budgets?referenceMonth=2026-08"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<BudgetResponse>>() {
                })
                .returnResult()
                .getResponseBody();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).referenceMonth()).isEqualTo(YearMonth.of(2026, 8));
    }

    @Test
    void getBudgetFromAnotherHousehold_returns404() {
        AuthenticatedTestUser owner = registerUser();
        UUID categoryId = createCategory(owner, "Lazer");
        UUID budgetId = createBudget(owner, categoryId, YearMonth.of(2026, 8), new BigDecimal("500.00"))
                .getResponseBody().id();
        AuthenticatedTestUser intruder = registerUser();

        intruder.authenticate(client.get().uri("/budgets/" + budgetId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateBudget_changesAmount() {
        AuthenticatedTestUser user = registerUser();
        UUID categoryId = createCategory(user, "Lazer");
        UUID budgetId = createBudget(user, categoryId, YearMonth.of(2026, 8), new BigDecimal("500.00"))
                .getResponseBody().id();

        EntityExchangeResult<BudgetResponse> result = client.put().uri("/budgets/" + budgetId)
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UpdateBudgetRequest(new BigDecimal("650.00")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(BudgetResponse.class)
                .returnResult();

        assertThat(result.getResponseBody().amount()).isEqualByComparingTo("650.00");
    }

    @Test
    void updateBudgetFromAnotherHousehold_returns404() {
        AuthenticatedTestUser owner = registerUser();
        UUID categoryId = createCategory(owner, "Lazer");
        UUID budgetId = createBudget(owner, categoryId, YearMonth.of(2026, 8), new BigDecimal("500.00"))
                .getResponseBody().id();
        AuthenticatedTestUser intruder = registerUser();

        client.put().uri("/budgets/" + budgetId)
                .cookie("JSESSIONID", intruder.sessionCookie())
                .cookie("XSRF-TOKEN", intruder.csrfToken())
                .header("X-XSRF-TOKEN", intruder.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UpdateBudgetRequest(new BigDecimal("1.00")))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteBudget_removesItForGood() {
        AuthenticatedTestUser user = registerUser();
        UUID categoryId = createCategory(user, "Lazer");
        UUID budgetId = createBudget(user, categoryId, YearMonth.of(2026, 8), new BigDecimal("500.00"))
                .getResponseBody().id();

        user.authenticate(client.delete().uri("/budgets/" + budgetId))
                .exchange()
                .expectStatus().isNoContent();

        user.authenticate(client.get().uri("/budgets/" + budgetId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void budgetSpentAndPercentageReflectOnlyPostedExpenseTransactionsOfTheSameHouseholdCategoryAndMonth() {
        AuthenticatedTestUser owner = registerUser();
        UUID accountId = createAccount(owner);
        UUID categoryId = createCategory(owner, "Lazer");
        YearMonth month = YearMonth.of(2026, 8);
        UUID budgetId = createBudget(owner, categoryId, month, new BigDecimal("200.00"))
                .getResponseBody().id();

        // conta pro gasto: POSTED + EXPENSE, categoria e mes certos
        createTransaction(owner, accountId, categoryId, new BigDecimal("30.00"),
                TransactionType.EXPENSE, TransactionStatus.POSTED, month);
        createTransaction(owner, accountId, categoryId, new BigDecimal("20.00"),
                TransactionType.EXPENSE, TransactionStatus.POSTED, month);
        // nao conta: PENDING
        createTransaction(owner, accountId, categoryId, new BigDecimal("999.00"),
                TransactionType.EXPENSE, TransactionStatus.PENDING, month);
        // nao conta: CANCELED
        createTransaction(owner, accountId, categoryId, new BigDecimal("999.00"),
                TransactionType.EXPENSE, TransactionStatus.CANCELED, month);
        // nao conta: INCOME (mesmo POSTED)
        createTransaction(owner, accountId, categoryId, new BigDecimal("999.00"),
                TransactionType.INCOME, TransactionStatus.POSTED, month);
        // nao conta: outro mes
        createTransaction(owner, accountId, categoryId, new BigDecimal("999.00"),
                TransactionType.EXPENSE, TransactionStatus.POSTED, month.plusMonths(1));
        // nao conta: outro household
        AuthenticatedTestUser other = registerUser();
        UUID otherAccountId = createAccount(other);
        UUID otherCategoryId = createCategory(other, "Lazer");
        createTransaction(other, otherAccountId, otherCategoryId, new BigDecimal("999.00"),
                TransactionType.EXPENSE, TransactionStatus.POSTED, month);

        BudgetResponse body = owner.authenticate(client.get().uri("/budgets/" + budgetId))
                .exchange()
                .expectStatus().isOk()
                .expectBody(BudgetResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.spent()).isEqualByComparingTo("50.00");
        assertThat(body.percentageUsed()).isEqualByComparingTo("25.0000");
    }
}
