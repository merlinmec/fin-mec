package com.mecfin.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.account.api.CreateAccountRequest;
import com.mecfin.account.domain.AccountType;
import com.mecfin.goal.api.GoalRequest;
import com.mecfin.identity.api.ChangePasswordRequest;
import com.mecfin.identity.api.LoginRequest;
import com.mecfin.identity.api.MfaCodeRequest;
import com.mecfin.identity.api.MfaLoginRequest;
import com.mecfin.identity.api.MfaSetupResponse;
import com.mecfin.identity.api.ReauthenticationRequest;
import com.mecfin.identity.api.RecoveryCodesResponse;
import com.mecfin.identity.api.SecurityEventResponse;
import com.mecfin.identity.api.UserResponse;
import com.mecfin.identity.application.TotpTestCodes;
import com.mecfin.identity.domain.SecurityEventType;
import com.mecfin.tag.api.TagRequest;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
import java.math.BigDecimal;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Fase 14: segurança da conta ponta a ponta. */
@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountSecurityIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final String PASSWORD = "s3cret1234";

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbc;

    /** Sessão de teste mutável: o cookie de sessão troca a cada login/renovação. */
    private static final class Session {
        String cookie;
        final String csrf;

        Session(String cookie, String csrf) {
            this.cookie = cookie;
            this.csrf = csrf;
        }

        void updateFrom(ExchangeResult result) {
            var renewed = result.getResponseCookies().getFirst("JSESSIONID");
            if (renewed != null) {
                cookie = renewed.getValue();
            }
        }
    }

    private String email() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private Session register(String email) {
        AuthenticatedTestUser user = AuthTestSupport.registerAndLogin(client, email, PASSWORD);
        return new Session(user.sessionCookie(), user.csrfToken());
    }

    private String freshCsrf() {
        return client.get().uri("/api/csrf").exchange().returnResult().getResponseCookies().getFirst("XSRF-TOKEN").getValue();
    }

    private RestTestClient.RequestBodySpec post(Session session, String uri) {
        RestTestClient.RequestBodySpec spec = client.post().uri(uri)
                .cookie("XSRF-TOKEN", session.csrf)
                .header("X-XSRF-TOKEN", session.csrf)
                .contentType(MediaType.APPLICATION_JSON);
        return session.cookie == null ? spec : spec.cookie("JSESSIONID", session.cookie);
    }

    private ExchangeResult login(Session session, String email, String password) {
        ExchangeResult result = post(session, "/api/auth/login")
                .body(new LoginRequest(email, password))
                .exchange()
                .expectBody(String.class)
                .returnResult();
        session.updateFrom(result);
        return result;
    }

    private Session anonymous() {
        return new Session(null, freshCsrf());
    }

    private HttpStatus me(Session session) {
        return HttpStatus.valueOf(client.get().uri("/api/auth/me").cookie("JSESSIONID", session.cookie)
                .exchange().returnResult().getStatus().value());
    }

    @Test
    void responsesCarrySecurityHeaders() {
        client.get().uri("/api/csrf").exchange()
                .expectHeader().valueMatches("Content-Security-Policy", ".*default-src 'self'.*frame-ancestors 'none'.*")
                .expectHeader().valueEquals("X-Frame-Options", "DENY")
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin")
                .expectHeader().valueMatches("Permissions-Policy", ".*camera=\\(\\).*");
    }

    @Test
    void loginRotatesTheSessionIdAgainstSessionFixation() {
        String email = email();
        Session first = register(email);
        String planted = first.cookie;

        ExchangeResult result = login(first, email, PASSWORD);

        assertThat(result.getStatus().value()).isEqualTo(200);
        assertThat(first.cookie).isNotEqualTo(planted);
    }

    @Test
    void changingThePasswordKeepsTheCurrentSessionAndRevokesTheOthers() {
        String email = email();
        Session laptop = register(email);
        Session phone = anonymous();
        login(phone, email, PASSWORD);
        assertThat(me(phone)).isEqualTo(HttpStatus.OK);

        ExchangeResult change = post(laptop, "/api/account/password")
                .body(new ChangePasswordRequest(PASSWORD, "outra senha forte 2026"))
                .exchange().expectStatus().isNoContent().returnResult();
        laptop.updateFrom(change);

        assertThat(me(laptop)).isEqualTo(HttpStatus.OK);
        assertThat(me(phone)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(anonymous(), email, PASSWORD).getStatus().value()).isEqualTo(401);
        assertThat(login(anonymous(), email, "outra senha forte 2026").getStatus().value()).isEqualTo(200);
    }

    @Test
    void wrongCurrentPasswordIs400AndDoesNotEndTheSession() {
        Session session = register(email());

        post(session, "/api/account/password")
                .body(new ChangePasswordRequest("not-my-password", "outra senha forte 2026"))
                .exchange().expectStatus().isBadRequest();

        assertThat(me(session)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void repeatedFailuresLockTheAccountEvenForTheRightPassword() {
        String email = email();
        Session owner = register(email);

        for (int i = 0; i < 3; i++) {
            assertThat(login(anonymous(), email, "wrong-password-" + i).getStatus().value()).isEqualTo(401);
        }
        assertThat(login(anonymous(), email, PASSWORD).getStatus().value()).isEqualTo(429);

        assertThat(events(owner)).extracting(SecurityEventResponse::type)
                .contains(SecurityEventType.LOGIN_FAILURE, SecurityEventType.ACCOUNT_LOCKED);
    }

    @Test
    void twoFactorFlowWithAuthenticatorAndSingleUseRecoveryCodes() {
        String email = email();
        Session session = register(email);

        MfaSetupResponse setup = post(session, "/api/account/2fa/setup").exchange().expectStatus().isOk()
                .expectBody(MfaSetupResponse.class).returnResult().getResponseBody();
        assertThat(setup.otpauthUri()).startsWith("otpauth://totp/fin-mec:").contains("secret=" + setup.secret());

        post(session, "/api/account/2fa/enable").body(new MfaCodeRequest("000000"))
                .exchange().expectStatus().isBadRequest();
        var enable = post(session, "/api/account/2fa/enable")
                .body(new MfaCodeRequest(TotpTestCodes.code(setup.secret(), 0)))
                .exchange().expectStatus().isOk()
                .expectBody(RecoveryCodesResponse.class).returnResult();
        session.updateFrom(enable);
        List<String> recoveryCodes = enable.getResponseBody().recoveryCodes();
        assertThat(recoveryCodes).hasSize(10).allMatch(code -> code.matches("[A-Z2-9]{5}-[A-Z2-9]{5}"));
        assertThat(client.get().uri("/api/auth/me").cookie("JSESSIONID", session.cookie).exchange()
                .expectStatus().isOk().expectBody(UserResponse.class).returnResult().getResponseBody().mfaEnabled())
                .isTrue();

        // senha certa agora só abre o desafio - nada autenticado ainda
        Session device = anonymous();
        ExchangeResult first = login(device, email, PASSWORD);
        assertThat(first.getStatus().value()).isEqualTo(202);
        assertThat(me(device)).isEqualTo(HttpStatus.UNAUTHORIZED);
        mfa(device, "123456", 401);
        // próximo passo (o atual já foi usado na ativação - anti-replay)
        mfa(device, TotpTestCodes.code(setup.secret(), 1), 200);
        assertThat(me(device)).isEqualTo(HttpStatus.OK);

        // código de recuperação: vale uma vez só
        Session lostPhone = anonymous();
        login(lostPhone, email, PASSWORD);
        mfa(lostPhone, recoveryCodes.get(0).toLowerCase(), 200);
        Session again = anonymous();
        login(again, email, PASSWORD);
        mfa(again, recoveryCodes.get(0), 401);

        // desligar exige senha + segundo fator
        post(device, "/api/account/2fa/disable").body(new ReauthenticationRequest(PASSWORD, null))
                .exchange().expectStatus().isBadRequest();
        ExchangeResult disabled = post(device, "/api/account/2fa/disable")
                .body(new ReauthenticationRequest(PASSWORD, recoveryCodes.get(1)))
                .exchange().expectStatus().isNoContent().returnResult();
        device.updateFrom(disabled);
        assertThat(login(anonymous(), email, PASSWORD).getStatus().value()).isEqualTo(200);

        assertThat(events(device)).extracting(SecurityEventResponse::type).contains(
                SecurityEventType.MFA_ENABLED, SecurityEventType.LOGIN_MFA_FAILURE,
                SecurityEventType.RECOVERY_CODE_USED, SecurityEventType.MFA_DISABLED);
    }

    @Test
    void mfaStepWithoutPendingChallengeIsRejected() {
        mfa(anonymous(), "123456", 401);
    }

    @Test
    void deletingTheAccountErasesEveryHouseholdRow() {
        String email = email();
        Session session = register(email);
        post(session, "/api/accounts").body(new CreateAccountRequest("Conta", AccountType.CHECKING, BigDecimal.TEN))
                .exchange().expectStatus().isCreated();
        post(session, "/api/tags").body(new TagRequest("Viagem", null)).exchange().expectStatus().isCreated();
        post(session, "/api/goals").body(new GoalRequest("Meta", BigDecimal.TEN, null, null, null, false))
                .exchange().expectStatus().isCreated();
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
        UUID householdId = jdbc.queryForObject(
                "SELECT household_id FROM household_members WHERE user_id = ?", UUID.class, userId);

        post(session, "/api/account/delete").body(new ReauthenticationRequest("wrong-password", null))
                .exchange().expectStatus().isBadRequest();
        post(session, "/api/account/delete").body(new ReauthenticationRequest(PASSWORD, null))
                .exchange().expectStatus().isNoContent();

        assertThat(me(session)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(anonymous(), email, PASSWORD).getStatus().value()).isEqualTo(401);
        for (String table : List.of("accounts", "tags", "goals", "categories", "budgets", "bills", "credit_cards",
                "notifications", "recurring_series", "household_members")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE household_id = ?",
                    Long.class, householdId)).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM households WHERE id = ?", Long.class, householdId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_events WHERE user_id = ?", Long.class, userId))
                .isZero();
    }

    private void mfa(Session session, String code, int expectedStatus) {
        ExchangeResult result = post(session, "/api/auth/login/mfa").body(new MfaLoginRequest(code))
                .exchange().expectStatus().isEqualTo(expectedStatus).returnResult();
        session.updateFrom(result);
    }

    private List<SecurityEventResponse> events(Session session) {
        return client.get().uri("/api/account/security-events").cookie("JSESSIONID", session.cookie)
                .exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<SecurityEventResponse>>() {
                })
                .returnResult().getResponseBody();
    }
}
