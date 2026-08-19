package com.mecfin.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.account.api.AccountResponse;
import com.mecfin.account.api.CreateAccountRequest;
import com.mecfin.account.api.UpdateAccountRequest;
import com.mecfin.account.domain.AccountType;
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
class AccountIT {

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

    // AuthTestSupport.authenticate() devolve RequestHeadersSpec<?> (sem contentType()/body()),
    // então requisições com corpo montam cookie+header de sessão manualmente, como o AuthIT já faz.
    private EntityExchangeResult<AccountResponse> createAccount(AuthenticatedTestUser user, String name) {
        return client.post().uri("/accounts")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateAccountRequest(name, AccountType.CHECKING, new BigDecimal("100.00")))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AccountResponse.class)
                .returnResult();
    }

    @Test
    void createAccount_returns201WithAccountScopedToOwnHousehold() {
        AuthenticatedTestUser user = registerUser();

        EntityExchangeResult<AccountResponse> result = createAccount(user, "Conta Corrente");

        AccountResponse body = result.getResponseBody();
        assertThat(body.id()).isNotNull();
        assertThat(body.name()).isEqualTo("Conta Corrente");
        assertThat(body.type()).isEqualTo(AccountType.CHECKING);
        assertThat(body.initialBalance()).isEqualByComparingTo("100.00");
        assertThat(body.currency()).isEqualTo("BRL");
        assertThat(body.archived()).isFalse();
    }

    @Test
    void createAccountWithoutSession_returns401() {
        client.post().uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateAccountRequest("Conta", AccountType.CHECKING, BigDecimal.ZERO))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void listAccounts_returnsOnlyAccountsFromOwnHousehold() {
        AuthenticatedTestUser owner = registerUser();
        createAccount(owner, "Conta A");
        createAccount(owner, "Conta B");
        AuthenticatedTestUser other = registerUser();
        createAccount(other, "Conta de Outro Household");

        EntityExchangeResult<List<AccountResponse>> result = owner.authenticate(client.get().uri("/accounts"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<List<AccountResponse>>() {
                })
                .returnResult();

        assertThat(result.getResponseBody())
                .extracting(AccountResponse::name)
                .containsExactly("Conta A", "Conta B");
    }

    @Test
    void getAccountFromAnotherHousehold_returns404() {
        AuthenticatedTestUser owner = registerUser();
        UUID accountId = createAccount(owner, "Conta Privada").getResponseBody().id();
        AuthenticatedTestUser intruder = registerUser();

        intruder.authenticate(client.get().uri("/accounts/" + accountId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateAccount_changesNameTypeAndArchivedFlag() {
        AuthenticatedTestUser owner = registerUser();
        UUID accountId = createAccount(owner, "Conta Original").getResponseBody().id();

        EntityExchangeResult<AccountResponse> result = client.put().uri("/accounts/" + accountId)
                .cookie("JSESSIONID", owner.sessionCookie())
                .cookie("XSRF-TOKEN", owner.csrfToken())
                .header("X-XSRF-TOKEN", owner.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UpdateAccountRequest("Conta Renomeada", AccountType.SAVINGS, true))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AccountResponse.class)
                .returnResult();

        AccountResponse body = result.getResponseBody();
        assertThat(body.name()).isEqualTo("Conta Renomeada");
        assertThat(body.type()).isEqualTo(AccountType.SAVINGS);
        assertThat(body.archived()).isTrue();
    }

    @Test
    void updateAccountFromAnotherHousehold_returns404() {
        AuthenticatedTestUser owner = registerUser();
        UUID accountId = createAccount(owner, "Conta Privada").getResponseBody().id();
        AuthenticatedTestUser intruder = registerUser();

        client.put().uri("/accounts/" + accountId)
                .cookie("JSESSIONID", intruder.sessionCookie())
                .cookie("XSRF-TOKEN", intruder.csrfToken())
                .header("X-XSRF-TOKEN", intruder.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UpdateAccountRequest("Hackeada", AccountType.CHECKING, false))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteAccount_softDeletesAndSubsequentGetReturns404() {
        AuthenticatedTestUser owner = registerUser();
        UUID accountId = createAccount(owner, "Conta a Excluir").getResponseBody().id();

        owner.authenticate(client.delete().uri("/accounts/" + accountId))
                .exchange()
                .expectStatus().isNoContent();

        owner.authenticate(client.get().uri("/accounts/" + accountId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteAccountFromAnotherHousehold_returns404() {
        AuthenticatedTestUser owner = registerUser();
        UUID accountId = createAccount(owner, "Conta Privada").getResponseBody().id();
        AuthenticatedTestUser intruder = registerUser();

        intruder.authenticate(client.delete().uri("/accounts/" + accountId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }
}
