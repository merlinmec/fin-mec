package com.mecfin.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.account.api.AccountResponse;
import com.mecfin.account.api.CreateAccountRequest;
import com.mecfin.account.domain.AccountType;
import com.mecfin.category.api.CategoryResponse;
import com.mecfin.category.api.CreateCategoryRequest;
import com.mecfin.category.domain.CategoryType;
import com.mecfin.report.application.CashFlowMonth;
import com.mecfin.report.application.CashFlowReport;
import com.mecfin.report.application.CategoryReport;
import com.mecfin.report.application.CategoryReportLine;
import com.mecfin.shared.web.PagedResponse;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
import com.mecfin.transaction.api.CreateTransactionRequest;
import com.mecfin.transaction.api.CreateTransferRequest;
import com.mecfin.transaction.api.TransactionResponse;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
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

/**
 * Fase 12: relatórios (fluxo de caixa, categorias), busca avançada e exportação CSV.
 * Cenário fixo em jun-ago/2026 para os números serem conferíveis à mão.
 */
@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReportIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final YearMonth JUN = YearMonth.of(2026, 6);
    private static final YearMonth JUL = YearMonth.of(2026, 7);
    private static final YearMonth AUG = YearMonth.of(2026, 8);

    @Autowired
    private RestTestClient client;

    private AuthenticatedTestUser registerUser() {
        return AuthTestSupport.registerAndLogin(client, "user-" + UUID.randomUUID() + "@example.com", "s3cret1234");
    }

    private RestTestClient.RequestBodySpec withBody(AuthenticatedTestUser user, RestTestClient.RequestBodySpec spec) {
        return spec
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private UUID createAccount(AuthenticatedTestUser user, String name, String initial) {
        return withBody(user, client.post().uri("/api/accounts"))
                .body(new CreateAccountRequest(name, AccountType.CHECKING, new BigDecimal(initial)))
                .exchange().expectStatus().isCreated()
                .expectBody(AccountResponse.class).returnResult().getResponseBody().id();
    }

    private UUID createCategory(AuthenticatedTestUser user, String name) {
        return withBody(user, client.post().uri("/api/categories"))
                .body(new CreateCategoryRequest(name, CategoryType.EXPENSE, null, "#16a34a", "home"))
                .exchange().expectStatus().isCreated()
                .expectBody(CategoryResponse.class).returnResult().getResponseBody().id();
    }

    private void tx(AuthenticatedTestUser user, UUID accountId, UUID categoryId, TransactionType type, String amount,
            String description, YearMonth month, TransactionStatus status) {
        withBody(user, client.post().uri("/api/transactions"))
                .body(new CreateTransactionRequest(accountId, categoryId, type, new BigDecimal(amount), description,
                        month.atDay(10), month, status, null))
                .exchange().expectStatus().isCreated();
    }

    /**
     * Saldo inicial 1000. Jun: +5000 / -1000 (Aluguel) -500 (Mercado). Jul: +5000 / -1000
     * (Aluguel) -1500 (Mercado) + previsto -200. Ago: -1000 (Aluguel). Transferência de 300 entre
     * contas em julho não é receita nem despesa e não muda o saldo total.
     */
    private record Scenario(AuthenticatedTestUser user, UUID checking, UUID housing, UUID food) {
    }

    private Scenario scenario() {
        AuthenticatedTestUser user = registerUser();
        UUID checking = createAccount(user, "Corrente", "1000.00");
        UUID savings = createAccount(user, "Poupança", "0.00");
        UUID housing = createCategory(user, "Moradia");
        UUID food = createCategory(user, "Supermercado");
        tx(user, checking, null, TransactionType.INCOME, "5000.00", "Salário", JUN, null);
        tx(user, checking, housing, TransactionType.EXPENSE, "1000.00", "Aluguel", JUN, null);
        tx(user, checking, food, TransactionType.EXPENSE, "500.00", "Mercado", JUN, null);
        tx(user, checking, null, TransactionType.INCOME, "5000.00", "Salário", JUL, null);
        tx(user, checking, housing, TransactionType.EXPENSE, "1000.00", "Aluguel", JUL, null);
        tx(user, checking, food, TransactionType.EXPENSE, "1500.00", "Mercado 50% off", JUL, null);
        tx(user, checking, food, TransactionType.EXPENSE, "200.00", "Feira", JUL, TransactionStatus.PENDING);
        tx(user, checking, housing, TransactionType.EXPENSE, "1000.00", "Aluguel", AUG, null);
        withBody(user, client.post().uri("/api/transactions/transfers"))
                .body(new CreateTransferRequest(checking, savings, new BigDecimal("300.00"), "Reserva",
                        JUL.atDay(15), JUL, null))
                .exchange().expectStatus().isCreated();
        return new Scenario(user, checking, housing, food);
    }

    @Test
    void cashFlowReportsMonthlyTotalsAndRunningBalance() {
        Scenario s = scenario();

        CashFlowReport report = s.user().authenticate(client.get().uri("/api/reports/cash-flow?from=2026-06&to=2026-08"))
                .exchange().expectStatus().isOk()
                .expectBody(CashFlowReport.class).returnResult().getResponseBody();

        assertThat(report.openingBalance()).isEqualByComparingTo("1000.00");
        assertThat(report.months()).extracting(CashFlowMonth::month).containsExactly(JUN, JUL, AUG);

        CashFlowMonth jun = report.months().get(0);
        assertThat(jun.income()).isEqualByComparingTo("5000.00");
        assertThat(jun.expense()).isEqualByComparingTo("1500.00");
        assertThat(jun.closingBalance()).isEqualByComparingTo("4500.00");

        CashFlowMonth jul = report.months().get(1);
        assertThat(jul.expense()).isEqualByComparingTo("2500.00");
        assertThat(jul.pendingExpense()).isEqualByComparingTo("200.00");
        // transferência entre contas próprias não muda o saldo total
        assertThat(jul.closingBalance()).isEqualByComparingTo("7000.00");

        assertThat(report.months().get(2).closingBalance()).isEqualByComparingTo("6000.00");
        assertThat(report.totalIncome()).isEqualByComparingTo("10000.00");
        assertThat(report.totalExpense()).isEqualByComparingTo("5000.00");
        assertThat(report.averageMonthlyExpense()).isEqualByComparingTo("1666.67");
        assertThat(report.savingsRate()).isEqualByComparingTo("50.0");
    }

    @Test
    void categoryReportComparesWithPreviousPeriod() {
        Scenario s = scenario();

        CategoryReport report = s.user().authenticate(client.get().uri("/api/reports/categories?from=2026-07&to=2026-07"))
                .exchange().expectStatus().isOk()
                .expectBody(CategoryReport.class).returnResult().getResponseBody();

        assertThat(report.total()).isEqualByComparingTo("2500.00");
        assertThat(report.previousTotal()).isEqualByComparingTo("1500.00");
        assertThat(report.changePercent()).isEqualByComparingTo("66.7");

        CategoryReportLine food = report.categories().get(0);
        assertThat(food.name()).isEqualTo("Supermercado");
        assertThat(food.total()).isEqualByComparingTo("1500.00"); // o previsto de 200 não entra
        assertThat(food.share()).isEqualByComparingTo("60.0");
        assertThat(food.changePercent()).isEqualByComparingTo("200.0");
        assertThat(report.categories().get(1).name()).isEqualTo("Moradia");
        assertThat(report.categories().get(1).changePercent()).isEqualByComparingTo("0.0");
    }

    @Test
    void reportRangeIsValidated() {
        AuthenticatedTestUser user = registerUser();
        user.authenticate(client.get().uri("/api/reports/cash-flow?from=2026-08&to=2026-06"))
                .exchange().expectStatus().isBadRequest();
        user.authenticate(client.get().uri("/api/reports/cash-flow?from=2020-01&to=2026-06"))
                .exchange().expectStatus().isBadRequest();
        user.authenticate(client.get().uri("/api/reports/categories?type=TRANSFER"))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void searchMatchesDescriptionCaseInsensitivelyAndTreatsWildcardsLiterally() {
        Scenario s = scenario();

        assertThat(searchText(s.user(), "MERCADO").totalElements()).isEqualTo(2);
        assertThat(searchText(s.user(), "50%").content())
                .singleElement().extracting(TransactionResponse::description).isEqualTo("Mercado 50% off");
        // "%%" seria "qualquer coisa" se os curingas do usuário não fossem escapados
        assertThat(searchText(s.user(), "%%").totalElements()).isZero();
    }

    @Test
    void searchCombinesDateAndAmountRanges() {
        Scenario s = scenario();

        PagedResponse<TransactionResponse> julyBigExpenses = search(s.user(),
                "?type=EXPENSE&from=2026-07-01&to=2026-07-31&minAmount=1000");
        assertThat(julyBigExpenses.content()).extracting(TransactionResponse::description)
                .containsExactlyInAnyOrder("Aluguel", "Mercado 50% off");
    }

    @Test
    void exportProducesExcelFriendlyCsvWithTheSameFilters() {
        Scenario s = scenario();

        byte[] body = s.user().authenticate(client.get().uri("/api/transactions/export?categoryId=" + s.housing()))
                .exchange().expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("text/csv")
                .expectHeader().valueMatches("Content-Disposition", "attachment; filename=\"lancamentos-.*\\.csv\"")
                .expectBody(byte[].class).returnResult().getResponseBody();

        assertThat(body).startsWith(0xEF, 0xBB, 0xBF);
        String csv = new String(body, StandardCharsets.UTF_8).substring(1);
        String[] lines = csv.split("\r\n");
        assertThat(lines[0]).isEqualTo("Data;Competência;Descrição;Categoria;Conta;Tipo;Situação;Valor");
        assertThat(lines).hasSize(4);
        assertThat(lines[1]).isEqualTo("10/08/2026;08/2026;\"Aluguel\";\"Moradia\";\"Corrente\";Despesa;Efetivado;-1000,00");
    }

    @Test
    void exportIsScopedToTheHousehold() {
        Scenario s = scenario();
        AuthenticatedTestUser intruder = registerUser();

        byte[] body = intruder.authenticate(client.get().uri("/api/transactions/export"))
                .exchange().expectStatus().isOk()
                .expectBody(byte[].class).returnResult().getResponseBody();

        assertThat(new String(body, StandardCharsets.UTF_8).split("\r\n")).hasSize(1);
        assertThat(s.checking()).isNotNull();
    }

    // Variável de URI (em vez de concatenar) para o cliente codificar "%" uma única vez.
    private PagedResponse<TransactionResponse> searchText(AuthenticatedTestUser user, String text) {
        return user.authenticate(client.get().uri("/api/transactions?q={q}", text))
                .exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<PagedResponse<TransactionResponse>>() {
                })
                .returnResult().getResponseBody();
    }

    private PagedResponse<TransactionResponse> search(AuthenticatedTestUser user, String query) {
        return user.authenticate(client.get().uri("/api/transactions" + query))
                .exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<PagedResponse<TransactionResponse>>() {
                })
                .returnResult().getResponseBody();
    }
}
