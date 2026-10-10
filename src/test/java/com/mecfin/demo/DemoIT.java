package com.mecfin.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mecfin.account.infra.AccountRepository;
import com.mecfin.dashboard.api.AccountBalanceResponse;
import com.mecfin.demo.application.DemoDataSeeder;
import com.mecfin.demo.application.DemoWarmup;
import com.mecfin.dashboard.api.DashboardResponse;
import com.mecfin.identity.api.ChangePasswordRequest;
import com.mecfin.identity.api.RegisterRequest;
import com.mecfin.identity.api.UserResponse;
import com.mecfin.identity.application.VisitorAccountService;
import com.mecfin.identity.domain.User;
import com.mecfin.identity.infra.UserRepository;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"mecfin.demo.enabled=true", "mecfin.demo.warmup=false",
                "mecfin.demo.sessions-per-ip-per-hour=1000"})
class DemoIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private RestTestClient client;

    @Autowired
    private VisitorAccountService visitors;

    @Autowired
    private UserRepository users;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private UserDetailsService userDetailsService;

    private String csrf() {
        return client.get().uri("/api/csrf").exchange().returnResult()
                .getResponseCookies().getFirst("XSRF-TOKEN").getValue();
    }

    private AuthenticatedTestUser startVisitor() {
        String token = csrf();
        EntityExchangeResult<UserResponse> result = client.post().uri("/api/demo/session")
                .cookie("XSRF-TOKEN", token)
                .header("X-XSRF-TOKEN", token)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponse.class)
                .returnResult();
        String session = result.getResponseCookies().getFirst("JSESSIONID").getValue();
        String newToken = client.get().uri("/api/csrf").cookie("JSESSIONID", session).exchange().returnResult()
                .getResponseCookies().getFirst("XSRF-TOKEN").getValue();
        UserResponse body = result.getResponseBody();
        return new AuthenticatedTestUser(body.id(), body.email(), session, newToken);
    }

    private DashboardResponse dashboard(AuthenticatedTestUser user) {
        return user.authenticate(client.get().uri("/api/dashboard"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(DashboardResponse.class)
                .returnResult().getResponseBody();
    }

    @Test
    void status_reportsDemoEnabled() {
        client.get().uri("/api/demo/status").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.enabled").isEqualTo(true);
    }

    @Test
    void visitorSession_createsSeededAccountAndLogsIn() {
        AuthenticatedTestUser visitor = startVisitor();

        assertThat(VisitorAccountService.isVisitor(visitor.email())).isTrue();
        DashboardResponse dashboard = dashboard(visitor);
        // Só o que não depende do dia do mês em que o teste roda (no dia 1 quase nada do mês
        // corrente já aconteceu).
        assertThat(dashboard.accountBalances()).hasSize(3);
        assertThat(dashboard.totalAvailableBalance()).isPositive();
        assertThat(dashboard.budgets()).hasSize(5);
    }

    @Test
    void visitors_doNotSeeEachOthersData() {
        AuthenticatedTestUser first = startVisitor();
        AuthenticatedTestUser second = startVisitor();

        assertThat(dashboard(first).accountBalances())
                .extracting(AccountBalanceResponse::accountId)
                .doesNotContainAnyElementsOf(dashboard(second).accountBalances().stream()
                        .map(AccountBalanceResponse::accountId).toList());
    }

    @Test
    void realSignupAndPasswordChange_areBlockedInDemo() {
        String token = csrf();
        client.post().uri("/api/auth/register")
                .cookie("XSRF-TOKEN", token)
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest("alguem-" + UUID.randomUUID() + "@example.com", "s3cret1234xyz"))
                .exchange()
                .expectStatus().isForbidden();

        AuthenticatedTestUser visitor = startVisitor();
        client.post().uri("/api/account/password")
                .cookie("JSESSIONID", visitor.sessionCookie())
                .cookie("XSRF-TOKEN", visitor.csrfToken())
                .header("X-XSRF-TOKEN", visitor.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ChangePasswordRequest("qualquer", "OutraSenha#2026"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void cleanup_deletesVisitorWithAllData_butNeverARealAccount() {
        AuthenticatedTestUser visitor = startVisitor();
        assertThat(visitors.createdBefore(Instant.now().plus(1, ChronoUnit.HOURS))).contains(visitor.userId());
        long accountsBefore = accounts.count();

        visitors.delete(visitor.userId());

        assertThat(users.findById(visitor.userId())).isEmpty();
        assertThat(accounts.count()).isEqualTo(accountsBefore - 3);
        visitor.authenticate(client.get().uri("/api/dashboard")).exchange().expectStatus().isUnauthorized();

        // Conta real (criada direto pelo repositório, já que o cadastro está bloqueado na demo).
        User real = users.saveAndFlush(new User(
                "pessoa-" + UUID.randomUUID() + "@example.com", "{noop}x"));
        assertThatThrownBy(() -> visitors.delete(real.getId())).isInstanceOf(IllegalArgumentException.class);
        assertThat(users.findById(real.getId())).isPresent();
    }

    @Test
    void warmup_runsTheWholeSeedAndLeavesNothingBehind() {
        long visitorsBefore = visitors.countActive();
        long accountsBefore = accounts.count();

        assertThat(new DemoWarmup(visitors, seeder, userDetailsService).warmUp()).isTrue();

        assertThat(visitors.countActive()).isEqualTo(visitorsBefore);
        assertThat(accounts.count()).isEqualTo(accountsBefore);
    }
}
