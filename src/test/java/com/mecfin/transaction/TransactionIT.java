package com.mecfin.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.account.api.AccountResponse;
import com.mecfin.account.api.CreateAccountRequest;
import com.mecfin.account.domain.AccountType;
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
class TransactionIT {

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

    // Mesmo padrao de AccountIT/CategoryIT: authenticate() nao serve para requisicoes com
    // corpo, entao cookie+header sao montados manualmente.
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

    private EntityExchangeResult<TransactionResponse> createTransaction(
            AuthenticatedTestUser user, UUID accountId, BigDecimal amount, YearMonth competenceMonth) {
        return client.post().uri("/transactions")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateTransactionRequest(
                        accountId, null, TransactionType.EXPENSE, amount, "Mercado",
                        LocalDate.now(), competenceMonth, null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TransactionResponse.class)
                .returnResult();
    }

    @Test
    void createTransaction_returns201WithPostedStatusByDefault() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);

        TransactionResponse body = createTransaction(user, accountId, new BigDecimal("50.00"), YearMonth.now())
                .getResponseBody();

        assertThat(body.id()).isNotNull();
        assertThat(body.accountId()).isEqualTo(accountId);
        assertThat(body.amount()).isEqualByComparingTo("50.00");
        assertThat(body.status()).isEqualTo(TransactionStatus.POSTED);
    }

    @Test
    void createTransactionWithoutSession_returns401() {
        client.post().uri("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateTransactionRequest(
                        UUID.randomUUID(), null, TransactionType.EXPENSE, BigDecimal.TEN, "Mercado",
                        LocalDate.now(), YearMonth.now(), null))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void createTransactionWithAccountFromAnotherHousehold_returns400() {
        AuthenticatedTestUser owner = registerUser();
        UUID accountId = createAccount(owner);
        AuthenticatedTestUser intruder = registerUser();

        client.post().uri("/transactions")
                .cookie("JSESSIONID", intruder.sessionCookie())
                .cookie("XSRF-TOKEN", intruder.csrfToken())
                .header("X-XSRF-TOKEN", intruder.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateTransactionRequest(
                        accountId, null, TransactionType.EXPENSE, BigDecimal.TEN, "Mercado",
                        LocalDate.now(), YearMonth.now(), null))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void listTransactions_returnsOnlyOwnHouseholdTransactions() {
        AuthenticatedTestUser owner = registerUser();
        UUID ownerAccountId = createAccount(owner);
        createTransaction(owner, ownerAccountId, new BigDecimal("10.00"), YearMonth.now());
        AuthenticatedTestUser other = registerUser();
        UUID otherAccountId = createAccount(other);
        createTransaction(other, otherAccountId, new BigDecimal("999.00"), YearMonth.now());

        List<TransactionResponse> result = owner.authenticate(client.get().uri("/transactions"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<TransactionResponse>>() {
                })
                .returnResult()
                .getResponseBody();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).accountId()).isEqualTo(ownerAccountId);
    }

    @Test
    void listTransactions_filtersByCompetenceMonth() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        createTransaction(user, accountId, new BigDecimal("10.00"), YearMonth.now());
        createTransaction(user, accountId, new BigDecimal("20.00"), YearMonth.now().minusMonths(1));

        List<TransactionResponse> result = user.authenticate(
                        client.get().uri("/transactions?competenceMonth=" + YearMonth.now()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<TransactionResponse>>() {
                })
                .returnResult()
                .getResponseBody();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void getTransactionFromAnotherHousehold_returns404() {
        AuthenticatedTestUser owner = registerUser();
        UUID accountId = createAccount(owner);
        UUID transactionId = createTransaction(owner, accountId, BigDecimal.TEN, YearMonth.now())
                .getResponseBody().id();
        AuthenticatedTestUser intruder = registerUser();

        intruder.authenticate(client.get().uri("/transactions/" + transactionId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateTransaction_changesAmountAndDescription() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        UUID transactionId = createTransaction(user, accountId, BigDecimal.TEN, YearMonth.now())
                .getResponseBody().id();

        EntityExchangeResult<TransactionResponse> result = client.put().uri("/transactions/" + transactionId)
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UpdateTransactionRequest(
                        null, TransactionType.EXPENSE, new BigDecimal("77.00"), "Farmacia",
                        LocalDate.now(), YearMonth.now(), TransactionStatus.POSTED))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TransactionResponse.class)
                .returnResult();

        assertThat(result.getResponseBody().amount()).isEqualByComparingTo("77.00");
        assertThat(result.getResponseBody().description()).isEqualTo("Farmacia");
    }

    @Test
    void deleteTransaction_cancelsInsteadOfHardDeleting() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        UUID transactionId = createTransaction(user, accountId, BigDecimal.TEN, YearMonth.now())
                .getResponseBody().id();

        user.authenticate(client.delete().uri("/transactions/" + transactionId))
                .exchange()
                .expectStatus().isNoContent();

        TransactionResponse body = user.authenticate(client.get().uri("/transactions/" + transactionId))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TransactionResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(body.status()).isEqualTo(TransactionStatus.CANCELED);
    }
}
