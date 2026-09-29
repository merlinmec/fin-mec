package com.mecfin.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.account.api.AccountResponse;
import com.mecfin.account.api.CreateAccountRequest;
import com.mecfin.account.domain.AccountType;
import com.mecfin.bill.api.BillResponse;
import com.mecfin.bill.api.CreateBillRequest;
import com.mecfin.bill.api.PayBillRequest;
import com.mecfin.bill.domain.BillStatus;
import com.mecfin.dashboard.api.DashboardResponse;
import com.mecfin.shared.domain.RecurrenceRule;
import com.mecfin.shared.web.PagedResponse;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
import com.mecfin.transaction.api.ConfirmTransactionRequest;
import com.mecfin.transaction.api.CreateTransactionRequest;
import com.mecfin.transaction.api.RecurringSeriesResponse;
import com.mecfin.transaction.api.TransactionResponse;
import com.mecfin.transaction.api.UpdateTransactionRequest;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
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

/**
 * Motor de recorrência (Fase 11) ponta a ponta: geração da janela, efetivação, edição e
 * exclusão "esta e as próximas", parar o fixo, efeito no dashboard e conta a pagar recorrente.
 */
@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecurrenceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

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

    private UUID createAccount(AuthenticatedTestUser user) {
        return withBody(user, client.post().uri("/api/accounts"))
                .body(new CreateAccountRequest("Conta", AccountType.CHECKING, new BigDecimal("1000.00")))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AccountResponse.class)
                .returnResult()
                .getResponseBody()
                .id();
    }

    private TransactionResponse createMonthlyRent(AuthenticatedTestUser user, UUID accountId, LocalDate endDate) {
        return withBody(user, client.post().uri("/api/transactions"))
                .body(new CreateTransactionRequest(accountId, null, TransactionType.EXPENSE, new BigDecimal("1500.00"),
                        "Aluguel", LocalDate.now(), YearMonth.now(), null, RecurrenceRule.MONTHLY, endDate))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TransactionResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private List<TransactionResponse> listAll(AuthenticatedTestUser user) {
        PagedResponse<TransactionResponse> page = user.authenticate(client.get().uri("/api/transactions?size=100"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<PagedResponse<TransactionResponse>>() {
                })
                .returnResult()
                .getResponseBody();
        return page.content().stream()
                .sorted(Comparator.comparing(TransactionResponse::recurrenceIndex))
                .toList();
    }

    @Test
    void creatingAMonthlyTransactionMaterializesTwelveMonthsAhead() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);

        TransactionResponse first = createMonthlyRent(user, accountId, null);

        assertThat(first.status()).isEqualTo(TransactionStatus.POSTED);
        assertThat(first.recurrenceSeriesId()).isNotNull();
        assertThat(first.recurrenceIndex()).isZero();

        List<TransactionResponse> occurrences = listAll(user);
        assertThat(occurrences).hasSize(13);
        assertThat(occurrences.subList(1, 13))
                .allSatisfy(occurrence -> assertThat(occurrence.status()).isEqualTo(TransactionStatus.PENDING));
        assertThat(occurrences.get(12).transactionDate()).isEqualTo(LocalDate.now().plusMonths(12));
    }

    @Test
    void pendingOccurrencesDoNotAffectBalanceButDriveTheProjection() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        TransactionResponse first = createMonthlyRent(user, accountId, null);
        TransactionResponse nextMonth = listAll(user).get(1);
        YearMonth next = YearMonth.from(nextMonth.transactionDate());

        DashboardResponse dashboard = user.authenticate(client.get().uri("/api/dashboard?month=" + next))
                .exchange()
                .expectStatus().isOk()
                .expectBody(DashboardResponse.class)
                .returnResult()
                .getResponseBody();

        // Saldo só tem a ocorrência efetivada (1000 - 1500); previsão desconta também a pendente.
        assertThat(dashboard.totalLedgerBalance()).isEqualByComparingTo("-500.00");
        assertThat(dashboard.pendingExpense()).isEqualByComparingTo("1500.00");
        assertThat(dashboard.projectedBalance()).isEqualByComparingTo("-2000.00");
        assertThat(first.id()).isNotEqualTo(nextMonth.id());
    }

    @Test
    void confirmPostsAPendingOccurrenceWithTheActualAmount() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        createMonthlyRent(user, accountId, null);
        TransactionResponse pending = listAll(user).get(1);

        TransactionResponse confirmed = withBody(user, client.post().uri("/api/transactions/" + pending.id() + "/confirm"))
                .body(new ConfirmTransactionRequest(new BigDecimal("1480.00"), null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TransactionResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(confirmed.status()).isEqualTo(TransactionStatus.POSTED);
        assertThat(confirmed.amount()).isEqualByComparingTo("1480.00");
        assertThat(confirmed.transactionDate()).isEqualTo(pending.transactionDate());

        withBody(user, client.post().uri("/api/transactions/" + pending.id() + "/confirm"))
                .body(new ConfirmTransactionRequest(null, null))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void editThisAndFutureUpdatesOnlyLaterPendingOccurrences() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        createMonthlyRent(user, accountId, null);
        TransactionResponse third = listAll(user).get(3);

        withBody(user, client.put().uri("/api/transactions/" + third.id() + "?scope=THIS_AND_FUTURE"))
                .body(new UpdateTransactionRequest(null, TransactionType.EXPENSE, new BigDecimal("1600.00"),
                        "Aluguel reajustado", third.transactionDate(), third.competenceMonth(),
                        TransactionStatus.PENDING, null))
                .exchange()
                .expectStatus().isOk();

        List<TransactionResponse> occurrences = listAll(user);
        assertThat(occurrences.subList(0, 3))
                .allSatisfy(occurrence -> assertThat(occurrence.amount()).isEqualByComparingTo("1500.00"));
        assertThat(occurrences.subList(3, 13)).allSatisfy(occurrence -> {
            assertThat(occurrence.amount()).isEqualByComparingTo("1600.00");
            assertThat(occurrence.description()).isEqualTo("Aluguel reajustado");
        });
    }

    @Test
    void deleteThisAndFutureCancelsTheTailAndEndsTheSeries() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        createMonthlyRent(user, accountId, null);
        TransactionResponse fifth = listAll(user).get(5);

        user.authenticate(client.delete().uri("/api/transactions/" + fifth.id() + "?scope=THIS_AND_FUTURE"))
                .exchange()
                .expectStatus().isNoContent();

        List<TransactionResponse> occurrences = listAll(user);
        assertThat(occurrences.subList(0, 5))
                .noneSatisfy(occurrence -> assertThat(occurrence.status()).isEqualTo(TransactionStatus.CANCELED));
        assertThat(occurrences.subList(5, 13))
                .allSatisfy(occurrence -> assertThat(occurrence.status()).isEqualTo(TransactionStatus.CANCELED));

        List<RecurringSeriesResponse> series = listSeries(user);
        assertThat(series).singleElement().satisfies(s -> {
            assertThat(s.active()).isFalse();
            assertThat(s.endDate()).isEqualTo(fifth.transactionDate().minusDays(1));
        });
    }

    @Test
    void endDateLimitsTheSeriesAndStopCancelsFuturePending() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        TransactionResponse first = createMonthlyRent(user, accountId, LocalDate.now().plusMonths(3));
        assertThat(listAll(user)).hasSize(4);

        withBody(user, client.post().uri("/api/recurring-series/" + first.recurrenceSeriesId() + "/stop"))
                .exchange()
                .expectStatus().isNoContent();

        assertThat(listAll(user).subList(1, 4))
                .allSatisfy(occurrence -> assertThat(occurrence.status()).isEqualTo(TransactionStatus.CANCELED));
    }

    @Test
    void seriesFromAnotherHouseholdIsInvisible() {
        AuthenticatedTestUser owner = registerUser();
        TransactionResponse first = createMonthlyRent(owner, createAccount(owner), null);
        AuthenticatedTestUser intruder = registerUser();

        assertThat(listSeries(intruder)).isEmpty();
        withBody(intruder, client.post().uri("/api/recurring-series/" + first.recurrenceSeriesId() + "/stop"))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void payingARecurringBillSchedulesTheNextOne() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        LocalDate due = LocalDate.now().plusDays(3);
        BillResponse bill = withBody(user, client.post().uri("/api/bills"))
                .body(new CreateBillRequest("Internet", new BigDecimal("120.00"), due, accountId, null,
                        RecurrenceRule.MONTHLY))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(BillResponse.class)
                .returnResult()
                .getResponseBody();

        withBody(user, client.post().uri("/api/bills/" + bill.id() + "/pay"))
                .body(new PayBillRequest(null, LocalDate.now(), null))
                .exchange()
                .expectStatus().isOk();

        List<BillResponse> open = user.authenticate(client.get().uri("/api/bills?status=OPEN"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<BillResponse>>() {
                })
                .returnResult()
                .getResponseBody();
        assertThat(open).singleElement().satisfies(next -> {
            assertThat(next.dueDate()).isEqualTo(due.plusMonths(1));
            assertThat(next.status()).isEqualTo(BillStatus.OPEN);
            assertThat(next.recurrenceRule()).isEqualTo(RecurrenceRule.MONTHLY);
        });
    }

    private List<RecurringSeriesResponse> listSeries(AuthenticatedTestUser user) {
        return user.authenticate(client.get().uri("/api/recurring-series"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<RecurringSeriesResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }
}
