package com.mecfin.household;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.account.api.AccountResponse;
import com.mecfin.account.api.CreateAccountRequest;
import com.mecfin.account.domain.AccountType;
import com.mecfin.household.application.HouseholdSharingService.InviteCreated;
import com.mecfin.household.application.HouseholdSharingService.InvitePreview;
import com.mecfin.household.application.HouseholdSharingService.Overview;
import com.mecfin.household.domain.HouseholdRole;
import com.mecfin.shared.web.PagedResponse;
import com.mecfin.tag.api.TagRequest;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.transaction.api.CreateTransactionRequest;
import com.mecfin.transaction.api.TransactionResponse;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Fase 17: household compartilhado ponta a ponta. */
@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HouseholdSharingIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final String PASSWORD = "s3cret1234";

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbc;

    /** Sessão de teste mutável: aceitar/sair troca o id da sessão (a resposta traz o cookie novo). */
    private static final class Session {
        final UUID userId;
        final String email;
        String cookie;
        String csrf;

        Session(AuthTestSupport.AuthenticatedTestUser user) {
            this.userId = user.userId();
            this.email = user.email();
            this.cookie = user.sessionCookie();
            this.csrf = user.csrfToken();
        }
    }

    private Session newUser() {
        return new Session(AuthTestSupport.registerAndLogin(client, "user-" + UUID.randomUUID() + "@example.com",
                PASSWORD));
    }

    private <S extends RestTestClient.RequestHeadersSpec<?>> S as(Session s, S spec) {
        spec.cookie("JSESSIONID", s.cookie).cookie("XSRF-TOKEN", s.csrf).header("X-XSRF-TOKEN", s.csrf);
        return spec;
    }

    private RestTestClient.RequestBodySpec post(Session s, String uri) {
        return as(s, client.post().uri(uri)).contentType(MediaType.APPLICATION_JSON);
    }

    private void adoptNewSessionCookie(Session s, EntityExchangeResult<?> result) {
        var cookie = result.getResponseCookies().getFirst("JSESSIONID");
        if (cookie != null) {
            s.cookie = cookie.getValue();
        }
    }

    private UUID householdOf(Session s) {
        return jdbc.queryForObject("SELECT household_id FROM household_members WHERE user_id = ?", UUID.class,
                s.userId);
    }

    private UUID createAccount(Session s, String name) {
        return post(s, "/api/accounts")
                .body(new CreateAccountRequest(name, AccountType.CHECKING, new BigDecimal("100.00")))
                .exchange().expectStatus().isCreated()
                .expectBody(AccountResponse.class).returnResult().getResponseBody().id();
    }

    private List<AccountResponse> accounts(Session s) {
        return as(s, client.get().uri("/api/accounts")).exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<AccountResponse>>() {
                }).returnResult().getResponseBody();
    }

    private String inviteToken(Session owner, String email) {
        InviteCreated created = post(owner, "/api/household/invites").body(Map.of("email", email))
                .exchange().expectStatus().isCreated()
                .expectBody(InviteCreated.class).returnResult().getResponseBody();
        assertThat(created.acceptUrl()).contains("/convite?token=");
        return created.acceptUrl().substring(created.acceptUrl().indexOf("token=") + "token=".length());
    }

    private Overview accept(Session s, String token, boolean discard) {
        EntityExchangeResult<Overview> result = post(s, "/api/household/invites/accept")
                .body(Map.of("token", token, "discardPersonalData", discard))
                .exchange().expectStatus().isOk()
                .expectBody(Overview.class).returnResult();
        adoptNewSessionCookie(s, result);
        return result.getResponseBody();
    }

    /** Dono com uma conta + membro que entrou por convite. */
    private Session[] sharedHousehold() {
        Session owner = newUser();
        createAccount(owner, "Conta da casa");
        Session member = newUser();
        accept(member, inviteToken(owner, member.email), false);
        return new Session[] {owner, member};
    }

    @Test
    void invitedMemberSeesAndWritesTheSameDataAndAuthorshipIsRecorded() {
        Session owner = newUser();
        UUID account = createAccount(owner, "Conta da casa");
        Session member = newUser();
        String token = inviteToken(owner, member.email.toUpperCase());

        InvitePreview preview = post(member, "/api/household/invites/preview").body(Map.of("token", token))
                .exchange().expectStatus().isOk()
                .expectBody(InvitePreview.class).returnResult().getResponseBody();
        assertThat(preview.emailMatches()).isTrue();
        assertThat(preview.hasPersonalData()).isFalse();
        assertThat(preview.invitedByEmail()).isEqualTo(owner.email);

        UUID oldHousehold = householdOf(member);
        Overview overview = accept(member, token, false);

        assertThat(overview.id()).isEqualTo(householdOf(owner));
        assertThat(overview.myRole()).isEqualTo(HouseholdRole.MEMBER);
        assertThat(overview.members()).extracting(m -> m.email()).containsExactly(owner.email, member.email);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM households WHERE id = ?", Long.class, oldHousehold))
                .isZero();
        // a sessão renovada já aponta para o household novo
        assertThat(accounts(member)).extracting(AccountResponse::id).containsExactly(account);

        post(member, "/api/transactions")
                .body(new CreateTransactionRequest(account, null, TransactionType.EXPENSE, new BigDecimal("42.00"),
                        "Mercado", LocalDate.now(), YearMonth.now(), TransactionStatus.POSTED, null))
                .exchange().expectStatus().isCreated();
        List<TransactionResponse> seenByOwner = as(owner, client.get().uri("/api/transactions"))
                .exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<PagedResponse<TransactionResponse>>() {
                }).returnResult().getResponseBody().content();
        assertThat(seenByOwner).singleElement().satisfies(t -> {
            assertThat(t.description()).isEqualTo("Mercado");
            assertThat(t.createdBy()).isEqualTo(member.userId);
        });

        // uso único
        post(member, "/api/household/invites/accept").body(Map.of("token", token, "discardPersonalData", false))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void inviteOnlyWorksForTheInvitedEmailAndCanBeRevoked() {
        Session owner = newUser();
        Session invited = newUser();
        Session stranger = newUser();
        String token = inviteToken(owner, invited.email);

        post(stranger, "/api/household/invites/accept").body(Map.of("token", token, "discardPersonalData", true))
                .exchange().expectStatus().isForbidden();
        post(stranger, "/api/household/invites/accept").body(Map.of("token", "forjado", "discardPersonalData", true))
                .exchange().expectStatus().isBadRequest();

        Overview overview = as(owner, client.get().uri("/api/household")).exchange().expectStatus().isOk()
                .expectBody(Overview.class).returnResult().getResponseBody();
        assertThat(overview.invites()).singleElement().satisfies(i -> assertThat(i.email()).isEqualTo(invited.email));
        as(owner, client.delete().uri("/api/household/invites/" + overview.invites().get(0).id()))
                .exchange().expectStatus().isNoContent();

        post(invited, "/api/household/invites/accept").body(Map.of("token", token, "discardPersonalData", false))
                .exchange().expectStatus().isBadRequest();
        assertThat(householdOf(invited)).isNotEqualTo(householdOf(owner));
    }

    @Test
    void acceptingWithPersonalDataNeedsExplicitConfirmationAndErasesEveryTable() {
        Session owner = newUser();
        Session invited = newUser();
        UUID ownAccount = createAccount(invited, "Minha conta");
        post(invited, "/api/tags").body(new TagRequest("viagem", "#aabbcc")).exchange().expectStatus().isCreated();
        post(invited, "/api/transactions")
                .body(new CreateTransactionRequest(ownAccount, null, TransactionType.INCOME, new BigDecimal("10.00"),
                        "Pix", LocalDate.now(), YearMonth.now(), TransactionStatus.POSTED, null))
                .exchange().expectStatus().isCreated();
        UUID oldHousehold = householdOf(invited);
        String token = inviteToken(owner, invited.email);

        post(invited, "/api/household/invites/accept").body(Map.of("token", token, "discardPersonalData", false))
                .exchange().expectStatus().isEqualTo(409);
        assertThat(householdOf(invited)).isEqualTo(oldHousehold);

        accept(invited, token, true);

        // Inventário do HouseholdDataEraser: nenhuma tabela com household_id pode sobrar linha.
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.columns
                WHERE table_schema = 'public' AND column_name = 'household_id'
                """, String.class);
        assertThat(tables).contains("accounts", "household_invites", "bank_account_links");
        for (String table : tables) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE household_id = ?", Long.class,
                    oldHousehold)).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE account_id = ?", Long.class,
                ownAccount)).isZero();
    }

    @Test
    void removedMemberLosesAccessImmediatelyAndStartsOver() {
        Session[] pair = sharedHousehold();
        Session owner = pair[0];
        Session member = pair[1];
        assertThat(accounts(member)).hasSize(1);

        as(owner, client.delete().uri("/api/household/members/" + member.userId)).exchange()
                .expectStatus().isNoContent();

        as(member, client.get().uri("/api/accounts")).exchange().expectStatus().isUnauthorized();
        Session again = new Session(AuthTestSupport.login(client, member.email, PASSWORD));
        assertThat(accounts(again)).isEmpty();
        assertThat(householdOf(again)).isNotEqualTo(householdOf(owner));
        Overview mine = as(again, client.get().uri("/api/household")).exchange().expectStatus().isOk()
                .expectBody(Overview.class).returnResult().getResponseBody();
        assertThat(mine.myRole()).isEqualTo(HouseholdRole.OWNER);
        assertThat(mine.members()).hasSize(1);
    }

    @Test
    void onlyTheOwnerManagesAndTheOwnerLeavesOnlyAfterTransferringOwnership() {
        Session[] pair = sharedHousehold();
        Session owner = pair[0];
        Session member = pair[1];

        post(member, "/api/household/invites").body(Map.of("email", "x@example.com"))
                .exchange().expectStatus().isForbidden();
        as(member, client.delete().uri("/api/household/members/" + owner.userId)).exchange()
                .expectStatus().isForbidden();
        as(member, client.put().uri("/api/household")).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Tomado")).exchange().expectStatus().isForbidden();
        post(owner, "/api/household/leave").exchange().expectStatus().isEqualTo(409);

        Overview transferred = post(owner, "/api/household/members/" + member.userId + "/owner")
                .exchange().expectStatus().isOk()
                .expectBody(Overview.class).returnResult().getResponseBody();
        assertThat(transferred.myRole()).isEqualTo(HouseholdRole.MEMBER);

        EntityExchangeResult<Overview> left = post(owner, "/api/household/leave").exchange().expectStatus().isOk()
                .expectBody(Overview.class).returnResult();
        adoptNewSessionCookie(owner, left);
        assertThat(left.getResponseBody().members()).hasSize(1);
        assertThat(accounts(owner)).isEmpty();
        assertThat(accounts(member)).hasSize(1);
    }

    @Test
    void deletingTheOwnersAccountHandsTheHouseholdToTheOldestMember() {
        Session[] pair = sharedHousehold();
        Session owner = pair[0];
        Session member = pair[1];

        post(owner, "/api/account/delete").body(Map.of("password", PASSWORD)).exchange().expectStatus().isNoContent();

        Overview overview = as(member, client.get().uri("/api/household")).exchange().expectStatus().isOk()
                .expectBody(Overview.class).returnResult().getResponseBody();
        assertThat(overview.myRole()).isEqualTo(HouseholdRole.OWNER);
        assertThat(overview.members()).hasSize(1);
        assertThat(accounts(member)).hasSize(1);
    }
}
