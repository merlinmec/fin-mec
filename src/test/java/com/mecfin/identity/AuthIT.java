package com.mecfin.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.identity.api.LoginRequest;
import com.mecfin.identity.api.RegisterRequest;
import com.mecfin.identity.api.UserResponse;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private RestTestClient client;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String freshCsrfToken() {
        ExchangeResult bootstrap = client.get().uri("/csrf").exchange().returnResult();
        return bootstrap.getResponseCookies().getFirst("XSRF-TOKEN").getValue();
    }

    private EntityExchangeResult<String> attemptLogin(String email, String password) {
        String token = freshCsrfToken();
        return client.post().uri("/auth/login")
                .cookie("XSRF-TOKEN", token)
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, password))
                .exchange()
                .expectBody(String.class)
                .returnResult();
    }

    @Test
    void registerSuccess_returns201AndAutoLogsIn() {
        String email = uniqueEmail();

        AuthenticatedTestUser user = AuthTestSupport.registerAndLogin(client, email, "s3cret1234");

        assertThat(user.email()).isEqualTo(email);
        assertThat(user.userId()).isNotNull();
        assertThat(user.sessionCookie()).isNotBlank();
    }

    @Test
    void registerDuplicateEmail_returns409() {
        String email = uniqueEmail();
        AuthTestSupport.registerAndLogin(client, email, "s3cret1234");

        String token = freshCsrfToken();
        client.post().uri("/auth/register")
                .cookie("XSRF-TOKEN", token)
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, "s3cret1234"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void registerInvalidPayload_returns400WithValidationErrors() {
        String token = freshCsrfToken();

        client.post().uri("/auth/register")
                .cookie("XSRF-TOKEN", token)
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest("not-an-email", "short"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void loginSuccess_setsSessionCookie() {
        String email = uniqueEmail();
        AuthTestSupport.registerAndLogin(client, email, "s3cret1234");
        String token = freshCsrfToken();

        EntityExchangeResult<UserResponse> result = client.post().uri("/auth/login")
                .cookie("XSRF-TOKEN", token)
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, "s3cret1234"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponse.class)
                .returnResult();

        assertThat(result.getResponseCookies().getFirst("JSESSIONID")).isNotNull();
    }

    @Test
    void loginWrongPassword_and_loginNonexistentEmail_returnIdenticalGenericError() {
        String email = uniqueEmail();
        AuthTestSupport.registerAndLogin(client, email, "s3cret1234");

        EntityExchangeResult<String> wrongPassword = attemptLogin(email, "wrong-password-1");
        EntityExchangeResult<String> nonexistentEmail = attemptLogin(uniqueEmail(), "whatever-password");

        assertThat(wrongPassword.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(nonexistentEmail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getResponseBody()).isEqualTo(nonexistentEmail.getResponseBody());
    }

    @Test
    void protectedEndpointWithoutSession_returns401ProblemDetail() {
        client.get().uri("/auth/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType("application/problem+json;charset=UTF-8");
    }

    @Test
    void logout_invalidatesSession_subsequentMeReturns401() {
        AuthenticatedTestUser user = AuthTestSupport.registerAndLogin(client, uniqueEmail(), "s3cret1234");

        user.authenticate(client.post().uri("/auth/logout"))
                .exchange()
                .expectStatus().isNoContent();

        user.authenticate(client.get().uri("/auth/me"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void meEndpoint_returnsAuthenticatedUsersOwnData() {
        String email = uniqueEmail();
        AuthenticatedTestUser user = AuthTestSupport.registerAndLogin(client, email, "s3cret1234");

        user.authenticate(client.get().uri("/auth/me"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponse.class)
                .value(body -> assertThat(body.email()).isEqualTo(email));
    }

    @Test
    void rateLimit_returns429AfterThreshold() {
        String email = uniqueEmail();
        AuthTestSupport.registerAndLogin(client, email, "s3cret1234");

        for (int i = 0; i < 5; i++) {
            attemptLogin(email, "wrong-password-1");
        }

        EntityExchangeResult<String> sixthAttempt = attemptLogin(email, "wrong-password-1");

        assertThat(sixthAttempt.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
