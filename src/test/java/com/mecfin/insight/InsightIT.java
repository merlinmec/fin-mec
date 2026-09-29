package com.mecfin.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.account.api.AccountResponse;
import com.mecfin.account.api.CreateAccountRequest;
import com.mecfin.account.domain.AccountType;
import com.mecfin.bill.api.CreateBillRequest;
import com.mecfin.budget.api.CreateBudgetRequest;
import com.mecfin.category.api.CategoryResponse;
import com.mecfin.category.domain.CategoryType;
import com.mecfin.insight.application.BalanceForecast;
import com.mecfin.insight.application.MonthlySummary;
import com.mecfin.notification.api.NotificationResponse;
import com.mecfin.notification.domain.NotificationType;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Fase 19: previsão de saldo, resumo mensal e alertas inteligentes contra Postgres real. */
@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InsightIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private RestTestClient client;

    private AuthenticatedTestUser newUser() {
        return AuthTestSupport.registerAndLogin(client, "user-" + UUID.randomUUID() + "@example.com", "s3cret1234");
    }

    private RestTestClient.RequestBodySpec post(AuthenticatedTestUser user, String uri) {
        return client.post().uri(uri)
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private UUID account(AuthenticatedTestUser user, String initial) {
        return post(user, "/api/accounts")
                .body(new CreateAccountRequest("Conta", AccountType.CHECKING, new BigDecimal(initial)))
                .exchange().expectStatus().isCreated()
                .expectBody(AccountResponse.class).returnResult().getResponseBody().id();
    }

    private void tx(AuthenticatedTestUser user, UUID account, UUID category, TransactionType type, String amount,
            String description, LocalDate date, TransactionStatus status) {
        post(user, "/api/transactions")
                .body(new CreateTransactionRequest(account, category, type, new BigDecimal(amount), description, date,
                        YearMonth.from(date), status, null))
                .exchange().expectStatus().isCreated();
    }

    private List<CategoryResponse> expenseCategories(AuthenticatedTestUser user) {
        return user.authenticate(client.get().uri("/api/categories")).exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<CategoryResponse>>() {
                }).returnResult().getResponseBody().stream()
                .filter(c -> c.type() == CategoryType.EXPENSE)
                .toList();
    }

    private List<NotificationResponse> sync(AuthenticatedTestUser user) {
        return post(user, "/api/notifications/sync").exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<NotificationResponse>>() {
                }).returnResult().getResponseBody();
    }

    @Test
    void forecastAppliesEverythingScheduledAndFindsTheFirstNegativeDay() {
        AuthenticatedTestUser user = newUser();
        UUID account = account(user, "1000.00");
        LocalDate today = LocalDate.now();
        tx(user, account, null, TransactionType.EXPENSE, "300.00", "Agendado", today.plusDays(5),
                TransactionStatus.POSTED);
        tx(user, account, null, TransactionType.EXPENSE, "500.00", "Previsto", today.plusDays(10),
                TransactionStatus.PENDING);
        post(user, "/api/bills").body(new CreateBillRequest("Aluguel", new BigDecimal("400.00"), today.plusDays(20),
                null, null, null)).exchange().expectStatus().isCreated();
        post(user, "/api/bills").body(new CreateBillRequest("Atrasada", new BigDecimal("50.00"), today.minusDays(3),
                null, null, null)).exchange().expectStatus().isCreated();

        BalanceForecast forecast = user.authenticate(client.get().uri("/api/insights/forecast?days=30"))
                .exchange().expectStatus().isOk()
                .expectBody(BalanceForecast.class).returnResult().getResponseBody();

        assertThat(forecast.startBalance()).isEqualByComparingTo("1000.00");
        assertThat(forecast.points()).hasSize(31);
        // a conta atrasada sai hoje, marcada como atrasada
        assertThat(forecast.points().get(0).balance()).isEqualByComparingTo("950.00");
        assertThat(forecast.points().get(0).events()).singleElement()
                .satisfies(e -> assertThat(e.overdue()).isTrue());
        assertThat(forecast.points().get(10).balance()).isEqualByComparingTo("150.00");
        assertThat(forecast.firstNegativeDate()).isEqualTo(today.plusDays(20));
        assertThat(forecast.lowestBalance()).isEqualByComparingTo("-250.00");
        assertThat(forecast.endBalance()).isEqualByComparingTo("-250.00");
        assertThat(forecast.totalExpense()).isEqualByComparingTo("1250.00");

        user.authenticate(client.get().uri("/api/insights/forecast?days=2")).exchange().expectStatus().isBadRequest();
    }

    @Test
    void monthlySummaryDescribesTheLastClosedMonth() {
        AuthenticatedTestUser user = newUser();
        UUID account = account(user, "0");
        UUID category = expenseCategories(user).get(0).id();
        LocalDate lastMonth = YearMonth.now().minusMonths(1).atDay(10);
        tx(user, account, null, TransactionType.INCOME, "5000.00", "Salário", lastMonth, TransactionStatus.POSTED);
        tx(user, account, category, TransactionType.EXPENSE, "1000.00", "Compras", lastMonth, TransactionStatus.POSTED);
        tx(user, account, category, TransactionType.EXPENSE, "500.00", "Compras", lastMonth.minusMonths(1),
                TransactionStatus.POSTED);

        MonthlySummary summary = user.authenticate(client.get().uri("/api/insights/monthly-summary"))
                .exchange().expectStatus().isOk()
                .expectBody(MonthlySummary.class).returnResult().getResponseBody();

        assertThat(summary.month()).isEqualTo(YearMonth.now().minusMonths(1));
        assertThat(summary.savingsRate()).isEqualByComparingTo("80.0");
        assertThat(summary.expenseChangePercent()).isEqualByComparingTo("100.0");
        assertThat(summary.biggestIncrease()).isNotNull();
        assertThat(summary.biggestIncrease().categoryId()).isEqualTo(category);
        assertThat(summary.headlines()).first().asString().startsWith("Você guardou 80% da renda");
        assertThat(summary.headlines()).anyMatch(h -> h.startsWith("Os gastos ficaram 100% acima"));
    }

    @Test
    void smartAlertsReachTheBellOnlyOnce() {
        AuthenticatedTestUser user = newUser();
        UUID account = account(user, "10000.00");
        List<CategoryResponse> categories = expenseCategories(user);
        UUID budgeted = categories.get(0).id();
        UUID habit = categories.get(1).id();
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.now();

        post(user, "/api/budgets").body(new CreateBudgetRequest(budgeted, month, new BigDecimal("100.00")))
                .exchange().expectStatus().isCreated();
        tx(user, account, budgeted, TransactionType.EXPENSE, "85.00", "Mercado", today, TransactionStatus.POSTED);
        for (int i = 1; i <= 3; i++) {
            tx(user, account, habit, TransactionType.EXPENSE, "200.00", "Restaurante",
                    month.minusMonths(i).atDay(15), TransactionStatus.POSTED);
        }
        tx(user, account, habit, TransactionType.EXPENSE, "350.00", "Restaurante", today, TransactionStatus.POSTED);

        List<NotificationResponse> first = sync(user);
        assertThat(first).extracting(NotificationResponse::type)
                .contains(NotificationType.BUDGET_NEAR_LIMIT, NotificationType.CATEGORY_SPENDING_SPIKE)
                .doesNotContain(NotificationType.BUDGET_EXCEEDED);
        assertThat(first).filteredOn(n -> n.type() == NotificationType.CATEGORY_SPENDING_SPIKE).singleElement()
                .satisfies(n -> assertThat(n.message()).contains("75% acima da média"));

        // sincronizar de novo não duplica; estourar o orçamento é informação nova
        tx(user, account, budgeted, TransactionType.EXPENSE, "20.00", "Mercado", today, TransactionStatus.POSTED);
        List<NotificationResponse> second = sync(user);
        assertThat(second).filteredOn(n -> n.type() == NotificationType.BUDGET_NEAR_LIMIT).hasSize(1);
        assertThat(second).filteredOn(n -> n.type() == NotificationType.CATEGORY_SPENDING_SPIKE).hasSize(1);
        assertThat(second).filteredOn(n -> n.type() == NotificationType.BUDGET_EXCEEDED).singleElement()
                .satisfies(n -> assertThat(n.message()).contains("estourado"));
    }
}
