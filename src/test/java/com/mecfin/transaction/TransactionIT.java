package com.mecfin.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.account.api.AccountResponse;
import com.mecfin.account.api.CreateAccountRequest;
import com.mecfin.account.domain.AccountType;
import com.mecfin.shared.web.PagedResponse;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
import com.mecfin.transaction.api.CreateInstallmentRequest;
import com.mecfin.transaction.api.CreateTransactionRequest;
import com.mecfin.transaction.api.CreateTransferRequest;
import com.mecfin.transaction.api.TransactionResponse;
import com.mecfin.transaction.api.UpdateTransactionRequest;
import com.mecfin.transaction.domain.TransactionDirection;
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
                        LocalDate.now(), competenceMonth, null, null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(TransactionResponse.class)
                .returnResult();
    }

    private PagedResponse<TransactionResponse> listTransactions(AuthenticatedTestUser user, String query) {
        return user.authenticate(client.get().uri("/transactions" + (query == null ? "" : query)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<PagedResponse<TransactionResponse>>() {
                })
                .returnResult()
                .getResponseBody();
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
                        LocalDate.now(), YearMonth.now(), null, null))
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
                        LocalDate.now(), YearMonth.now(), null, null))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void createTransactionWithTypeTransfer_returns400() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);

        client.post().uri("/transactions")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateTransactionRequest(
                        accountId, null, TransactionType.TRANSFER, BigDecimal.TEN, "Transferencia",
                        LocalDate.now(), YearMonth.now(), null, null))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void createTransfer_createsTwoLinkedLegsOnDifferentAccounts() {
        AuthenticatedTestUser user = registerUser();
        UUID sourceAccountId = createAccount(user);
        UUID destinationAccountId = createAccount(user);

        EntityExchangeResult<List<TransactionResponse>> result = client.post().uri("/transactions/transfers")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateTransferRequest(
                        sourceAccountId, destinationAccountId, new BigDecimal("40.00"), "Transferencia",
                        LocalDate.now(), YearMonth.now(), null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<List<TransactionResponse>>() {
                })
                .returnResult();

        List<TransactionResponse> legs = result.getResponseBody();
        assertThat(legs).hasSize(2);
        TransactionResponse out = legs.get(0);
        TransactionResponse in = legs.get(1);
        assertThat(out.accountId()).isEqualTo(sourceAccountId);
        assertThat(out.transferDirection()).isEqualTo(TransactionDirection.OUT);
        assertThat(in.accountId()).isEqualTo(destinationAccountId);
        assertThat(in.transferDirection()).isEqualTo(TransactionDirection.IN);
        assertThat(out.transferPairId()).isEqualTo(in.id());
        assertThat(in.transferPairId()).isEqualTo(out.id());
    }

    @Test
    void cancelTransfer_cancelsBothLegs() {
        AuthenticatedTestUser user = registerUser();
        UUID sourceAccountId = createAccount(user);
        UUID destinationAccountId = createAccount(user);
        List<TransactionResponse> legs = client.post().uri("/transactions/transfers")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateTransferRequest(
                        sourceAccountId, destinationAccountId, new BigDecimal("40.00"), "Transferencia",
                        LocalDate.now(), YearMonth.now(), null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<List<TransactionResponse>>() {
                })
                .returnResult()
                .getResponseBody();

        // Confirma que transferPairId foi mesmo persistido (não só devolvido na resposta
        // imediata do POST, que reflete só o objeto em memória) antes de cancelar.
        TransactionResponse outBefore = user.authenticate(client.get().uri("/transactions/" + legs.get(0).id()))
                .exchange().expectStatus().isOk().expectBody(TransactionResponse.class).returnResult().getResponseBody();
        assertThat(outBefore.transferPairId()).isEqualTo(legs.get(1).id());

        user.authenticate(client.delete().uri("/transactions/" + legs.get(0).id()))
                .exchange()
                .expectStatus().isNoContent();

        TransactionResponse outAfter = user.authenticate(client.get().uri("/transactions/" + legs.get(0).id()))
                .exchange().expectStatus().isOk().expectBody(TransactionResponse.class).returnResult().getResponseBody();
        TransactionResponse inAfter = user.authenticate(client.get().uri("/transactions/" + legs.get(1).id()))
                .exchange().expectStatus().isOk().expectBody(TransactionResponse.class).returnResult().getResponseBody();
        assertThat(outAfter.status()).isEqualTo(TransactionStatus.CANCELED);
        assertThat(inAfter.status()).isEqualTo(TransactionStatus.CANCELED);
    }

    @Test
    void updateTransferLeg_returns409() {
        AuthenticatedTestUser user = registerUser();
        UUID sourceAccountId = createAccount(user);
        UUID destinationAccountId = createAccount(user);
        List<TransactionResponse> legs = client.post().uri("/transactions/transfers")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateTransferRequest(
                        sourceAccountId, destinationAccountId, new BigDecimal("40.00"), "Transferencia",
                        LocalDate.now(), YearMonth.now(), null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<List<TransactionResponse>>() {
                })
                .returnResult()
                .getResponseBody();

        client.put().uri("/transactions/" + legs.get(0).id())
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UpdateTransactionRequest(
                        null, TransactionType.EXPENSE, BigDecimal.ONE, "Hack",
                        LocalDate.now(), YearMonth.now(), TransactionStatus.POSTED, null))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createInstallments_materializesAllLegs() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);

        EntityExchangeResult<List<TransactionResponse>> result = client.post().uri("/transactions/installments")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateInstallmentRequest(
                        accountId, null, TransactionType.EXPENSE, new BigDecimal("100.00"), "TV",
                        LocalDate.of(2026, 8, 10), YearMonth.of(2026, 8), 3))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<List<TransactionResponse>>() {
                })
                .returnResult();

        List<TransactionResponse> legs = result.getResponseBody();
        assertThat(legs).hasSize(3);
        assertThat(legs.get(0).description()).isEqualTo("TV (1/3)");
        assertThat(legs.get(0).installmentNumber()).isEqualTo(1);
        assertThat(legs.get(0).installmentTotal()).isEqualTo(3);
        assertThat(legs.get(2).competenceMonth()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(legs).extracting(TransactionResponse::installmentGroupId).doesNotContainNull();
    }

    @Test
    void listTransactions_returnsOnlyOwnHouseholdTransactionsPaginated() {
        AuthenticatedTestUser owner = registerUser();
        UUID ownerAccountId = createAccount(owner);
        createTransaction(owner, ownerAccountId, new BigDecimal("10.00"), YearMonth.now());
        AuthenticatedTestUser other = registerUser();
        UUID otherAccountId = createAccount(other);
        createTransaction(other, otherAccountId, new BigDecimal("999.00"), YearMonth.now());

        PagedResponse<TransactionResponse> result = listTransactions(owner, null);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).accountId()).isEqualTo(ownerAccountId);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void listTransactions_filtersByCompetenceMonth() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        createTransaction(user, accountId, new BigDecimal("10.00"), YearMonth.now());
        createTransaction(user, accountId, new BigDecimal("20.00"), YearMonth.now().minusMonths(1));

        PagedResponse<TransactionResponse> result =
                listTransactions(user, "?competenceMonth=" + YearMonth.now());

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void listTransactions_respectsPageSize() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        for (int i = 0; i < 3; i++) {
            createTransaction(user, accountId, BigDecimal.TEN, YearMonth.now());
        }

        PagedResponse<TransactionResponse> result = listTransactions(user, "?page=0&size=2");

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
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
                        LocalDate.now(), YearMonth.now(), TransactionStatus.POSTED, null))
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
