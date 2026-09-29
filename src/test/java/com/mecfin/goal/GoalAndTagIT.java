package com.mecfin.goal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.account.api.AccountResponse;
import com.mecfin.account.api.CreateAccountRequest;
import com.mecfin.account.domain.AccountType;
import com.mecfin.goal.api.ContributionRequest;
import com.mecfin.goal.api.GoalContributionResponse;
import com.mecfin.goal.api.GoalRequest;
import com.mecfin.goal.api.GoalResponse;
import com.mecfin.goal.domain.GoalStatus;
import com.mecfin.shared.domain.RecurrenceRule;
import com.mecfin.shared.web.PagedResponse;
import com.mecfin.tag.api.TagRequest;
import com.mecfin.tag.api.TagResponse;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
import com.mecfin.transaction.api.CreateTransactionRequest;
import com.mecfin.transaction.api.TransactionResponse;
import com.mecfin.transaction.api.UpdateTransactionRequest;
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

/** Fase 13: tags em lançamentos e metas de economia. */
@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GoalAndTagIT {

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
                .body(new CreateAccountRequest("Conta", AccountType.CHECKING, BigDecimal.ZERO))
                .exchange().expectStatus().isCreated()
                .expectBody(AccountResponse.class).returnResult().getResponseBody().id();
    }

    private TagResponse createTag(AuthenticatedTestUser user, String name) {
        return withBody(user, client.post().uri("/api/tags"))
                .body(new TagRequest(name, "#2563eb"))
                .exchange().expectStatus().isCreated()
                .expectBody(TagResponse.class).returnResult().getResponseBody();
    }

    private TransactionResponse createTagged(AuthenticatedTestUser user, UUID accountId, String description,
            RecurrenceRule rule, List<UUID> tagIds) {
        return withBody(user, client.post().uri("/api/transactions"))
                .body(new CreateTransactionRequest(accountId, null, TransactionType.EXPENSE, new BigDecimal("80.00"),
                        description, LocalDate.now(), YearMonth.now(), null, rule, null, tagIds))
                .exchange().expectStatus().isCreated()
                .expectBody(TransactionResponse.class).returnResult().getResponseBody();
    }

    private PagedResponse<TransactionResponse> list(AuthenticatedTestUser user, String query) {
        return user.authenticate(client.get().uri("/api/transactions" + query))
                .exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<PagedResponse<TransactionResponse>>() {
                })
                .returnResult().getResponseBody();
    }

    // ---- tags ----

    @Test
    void tagNamesAreUniquePerHouseholdIgnoringCase() {
        AuthenticatedTestUser user = registerUser();
        createTag(user, "Viagem");

        withBody(user, client.post().uri("/api/tags"))
                .body(new TagRequest("  viagem ", null))
                .exchange().expectStatus().isEqualTo(409);

        // outro household pode ter o mesmo nome
        createTag(registerUser(), "Viagem");
    }

    @Test
    void transactionsCanBeTaggedFilteredByTagAndTagsReportUsage() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        TagResponse trip = createTag(user, "Viagem");
        TagResponse refundable = createTag(user, "Reembolsável");

        TransactionResponse hotel = createTagged(user, accountId, "Hotel", null, List.of(trip.id(), refundable.id()));
        createTagged(user, accountId, "Mercado", null, List.of());

        assertThat(hotel.tagIds()).containsExactlyInAnyOrder(trip.id(), refundable.id());
        assertThat(list(user, "?tagId=" + trip.id()).content())
                .singleElement().extracting(TransactionResponse::description).isEqualTo("Hotel");

        List<TagResponse> tags = user.authenticate(client.get().uri("/api/tags"))
                .exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<TagResponse>>() {
                })
                .returnResult().getResponseBody();
        assertThat(tags).extracting(TagResponse::usageCount).containsExactly(1L, 1L);
    }

    @Test
    void updateWithoutTagIdsKeepsTagsAndEmptyListClearsThem() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        TagResponse trip = createTag(user, "Viagem");
        TransactionResponse hotel = createTagged(user, accountId, "Hotel", null, List.of(trip.id()));

        TransactionResponse kept = withBody(user, client.put().uri("/api/transactions/" + hotel.id()))
                .body(new UpdateTransactionRequest(null, TransactionType.EXPENSE, new BigDecimal("90.00"), "Hotel",
                        hotel.transactionDate(), hotel.competenceMonth(), TransactionStatus.POSTED, null))
                .exchange().expectStatus().isOk()
                .expectBody(TransactionResponse.class).returnResult().getResponseBody();
        assertThat(kept.tagIds()).containsExactly(trip.id());

        TransactionResponse cleared = withBody(user, client.put().uri("/api/transactions/" + hotel.id()))
                .body(new UpdateTransactionRequest(null, TransactionType.EXPENSE, new BigDecimal("90.00"), "Hotel",
                        hotel.transactionDate(), hotel.competenceMonth(), TransactionStatus.POSTED, null, List.of()))
                .exchange().expectStatus().isOk()
                .expectBody(TransactionResponse.class).returnResult().getResponseBody();
        assertThat(cleared.tagIds()).isEmpty();
    }

    @Test
    void recurringOccurrencesInheritTheTags() {
        AuthenticatedTestUser user = registerUser();
        TagResponse subscription = createTag(user, "Assinatura");
        createTagged(user, createAccount(user), "Streaming", RecurrenceRule.MONTHLY, List.of(subscription.id()));

        PagedResponse<TransactionResponse> tagged = list(user, "?size=100&tagId=" + subscription.id());
        assertThat(tagged.totalElements()).isEqualTo(13);
    }

    @Test
    void tagFromAnotherHouseholdIsRejectedAndDeletingATagKeepsTheTransaction() {
        AuthenticatedTestUser owner = registerUser();
        TagResponse foreign = createTag(registerUser(), "Alheia");
        UUID accountId = createAccount(owner);

        withBody(owner, client.post().uri("/api/transactions"))
                .body(new CreateTransactionRequest(accountId, null, TransactionType.EXPENSE, BigDecimal.TEN, "X",
                        LocalDate.now(), YearMonth.now(), null, null, null, List.of(foreign.id())))
                .exchange().expectStatus().isBadRequest();

        TagResponse mine = createTag(owner, "Minha");
        TransactionResponse tx = createTagged(owner, accountId, "Café", null, List.of(mine.id()));
        owner.authenticate(client.delete().uri("/api/tags/" + mine.id())).exchange().expectStatus().isNoContent();

        TransactionResponse after = owner.authenticate(client.get().uri("/api/transactions/" + tx.id()))
                .exchange().expectStatus().isOk()
                .expectBody(TransactionResponse.class).returnResult().getResponseBody();
        assertThat(after.tagIds()).isEmpty();
        assertThat(after.status()).isEqualTo(TransactionStatus.POSTED);
    }

    // ---- metas ----

    private GoalResponse createGoal(AuthenticatedTestUser user, String target, LocalDate targetDate) {
        return withBody(user, client.post().uri("/api/goals"))
                .body(new GoalRequest("Reserva de emergência", new BigDecimal(target), targetDate, "#16a34a",
                        "shield", false))
                .exchange().expectStatus().isCreated()
                .expectBody(GoalResponse.class).returnResult().getResponseBody();
    }

    private GoalResponse contribute(AuthenticatedTestUser user, UUID goalId, String amount, int expectedStatus) {
        return withBody(user, client.post().uri("/api/goals/" + goalId + "/contributions"))
                .body(new ContributionRequest(new BigDecimal(amount), null, "aporte"))
                .exchange().expectStatus().isEqualTo(expectedStatus)
                .expectBody(GoalResponse.class).returnResult().getResponseBody();
    }

    @Test
    void contributionsDriveTheDerivedProgress() {
        AuthenticatedTestUser user = registerUser();
        GoalResponse goal = createGoal(user, "1000.00", LocalDate.now().plusMonths(3));
        assertThat(goal.status()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(goal.savedAmount()).isEqualByComparingTo("0");

        contribute(user, goal.id(), "300.00", 201);
        GoalResponse afterWithdraw = contribute(user, goal.id(), "-100.00", 201);

        assertThat(afterWithdraw.savedAmount()).isEqualByComparingTo("200.00");
        assertThat(afterWithdraw.savedThisMonth()).isEqualByComparingTo("200.00");
        assertThat(afterWithdraw.progressPercent()).isEqualByComparingTo("20.0");
        assertThat(afterWithdraw.remainingAmount()).isEqualByComparingTo("800.00");
        assertThat(afterWithdraw.monthsRemaining()).isEqualTo(4);
        assertThat(afterWithdraw.monthlyNeeded()).isEqualByComparingTo("200.00");

        GoalResponse completed = contribute(user, goal.id(), "800.00", 201);
        assertThat(completed.status()).isEqualTo(GoalStatus.COMPLETED);
        assertThat(completed.progressPercent()).isEqualByComparingTo("100");
    }

    @Test
    void withdrawingMoreThanSavedIsRejected() {
        AuthenticatedTestUser user = registerUser();
        GoalResponse goal = createGoal(user, "1000.00", null);
        contribute(user, goal.id(), "50.00", 201);

        withBody(user, client.post().uri("/api/goals/" + goal.id() + "/contributions"))
                .body(new ContributionRequest(new BigDecimal("-50.01"), null, null))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void deletingAContributionRecomputesTheGoal() {
        AuthenticatedTestUser user = registerUser();
        GoalResponse goal = createGoal(user, "1000.00", null);
        contribute(user, goal.id(), "100.00", 201);
        contribute(user, goal.id(), "250.00", 201);

        List<GoalContributionResponse> history = user.authenticate(
                        client.get().uri("/api/goals/" + goal.id() + "/contributions"))
                .exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<GoalContributionResponse>>() {
                })
                .returnResult().getResponseBody();
        assertThat(history).hasSize(2);
        UUID toRemove = history.stream().filter(c -> c.amount().compareTo(new BigDecimal("250")) == 0)
                .findFirst().orElseThrow().id();

        GoalResponse after = user.authenticate(client.delete().uri("/api/goals/" + goal.id() + "/contributions/" + toRemove))
                .exchange().expectStatus().isOk()
                .expectBody(GoalResponse.class).returnResult().getResponseBody();
        assertThat(after.savedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void archivedGoalsAreHiddenByDefaultAndRejectContributions() {
        AuthenticatedTestUser user = registerUser();
        GoalResponse goal = createGoal(user, "500.00", null);

        withBody(user, client.put().uri("/api/goals/" + goal.id()))
                .body(new GoalRequest(goal.name(), goal.targetAmount(), null, null, null, true))
                .exchange().expectStatus().isOk();

        assertThat(listGoals(user, "")).isEmpty();
        assertThat(listGoals(user, "?includeArchived=true")).singleElement()
                .extracting(GoalResponse::status).isEqualTo(GoalStatus.ARCHIVED);
        withBody(user, client.post().uri("/api/goals/" + goal.id() + "/contributions"))
                .body(new ContributionRequest(BigDecimal.TEN, null, null))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void goalsAreScopedToTheHousehold() {
        AuthenticatedTestUser owner = registerUser();
        GoalResponse goal = createGoal(owner, "500.00", null);
        AuthenticatedTestUser intruder = registerUser();

        assertThat(listGoals(intruder, "")).isEmpty();
        intruder.authenticate(client.get().uri("/api/goals/" + goal.id())).exchange().expectStatus().isNotFound();
        withBody(intruder, client.post().uri("/api/goals/" + goal.id() + "/contributions"))
                .body(new ContributionRequest(BigDecimal.TEN, null, null))
                .exchange().expectStatus().isNotFound();
    }

    private List<GoalResponse> listGoals(AuthenticatedTestUser user, String query) {
        return user.authenticate(client.get().uri("/api/goals" + query))
                .exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<GoalResponse>>() {
                })
                .returnResult().getResponseBody();
    }
}
