package com.mecfin.bill;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.account.api.AccountResponse;
import com.mecfin.account.api.CreateAccountRequest;
import com.mecfin.account.domain.AccountType;
import com.mecfin.bill.api.BillResponse;
import com.mecfin.bill.api.CreateBillRequest;
import com.mecfin.bill.api.PayBillRequest;
import com.mecfin.bill.api.UpdateBillRequest;
import com.mecfin.bill.domain.BillStatus;
import com.mecfin.category.api.CategoryResponse;
import com.mecfin.category.api.CreateCategoryRequest;
import com.mecfin.category.domain.CategoryType;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
import com.mecfin.transaction.api.TransactionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class BillIT {

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

    private UUID createAccount(AuthenticatedTestUser user) {
        return client.post().uri("/accounts")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateAccountRequest("Conta Corrente", AccountType.CHECKING, new BigDecimal("1000.00")))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AccountResponse.class)
                .returnResult()
                .getResponseBody()
                .id();
    }

    private UUID createCategory(AuthenticatedTestUser user) {
        return client.post().uri("/categories")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCategoryRequest("Moradia", CategoryType.EXPENSE, null, null, null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CategoryResponse.class)
                .returnResult()
                .getResponseBody()
                .id();
    }

    private EntityExchangeResult<BillResponse> createBill(
            AuthenticatedTestUser user, LocalDate dueDate, UUID sourceAccountId) {
        return client.post().uri("/bills")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateBillRequest("Aluguel", new BigDecimal("1500.00"), dueDate, sourceAccountId, null, null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(BillResponse.class)
                .returnResult();
    }

    @Test
    void createBill_returns201ScopedToOwnHouseholdWithOpenStatus() {
        AuthenticatedTestUser user = registerUser();

        BillResponse body = createBill(user, LocalDate.now().plusDays(10), null).getResponseBody();

        assertThat(body.id()).isNotNull();
        assertThat(body.description()).isEqualTo("Aluguel");
        assertThat(body.status()).isEqualTo(BillStatus.OPEN);
        assertThat(body.paidTransactionId()).isNull();
    }

    @Test
    void createBillWithoutSession_returns401() {
        client.post().uri("/bills")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateBillRequest("Aluguel", BigDecimal.TEN, LocalDate.now(), null, null, null))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void createBillWithInvisibleCategory_returns400() {
        AuthenticatedTestUser user = registerUser();

        client.post().uri("/bills")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateBillRequest("Aluguel", BigDecimal.TEN, LocalDate.now(), null, UUID.randomUUID(), null))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void createBillWithInvalidSourceAccount_returns400() {
        AuthenticatedTestUser user = registerUser();

        client.post().uri("/bills")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateBillRequest("Aluguel", BigDecimal.TEN, LocalDate.now(), UUID.randomUUID(), null, null))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void listBills_returnsOnlyOwnHousehold() {
        AuthenticatedTestUser owner = registerUser();
        createBill(owner, LocalDate.now().plusDays(5), null);
        AuthenticatedTestUser other = registerUser();
        createBill(other, LocalDate.now().plusDays(5), null);

        List<BillResponse> result = owner.authenticate(client.get().uri("/bills"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<BillResponse>>() {
                })
                .returnResult()
                .getResponseBody();

        assertThat(result).hasSize(1);
    }

    @Test
    void listBills_computesOverdueStatusWithoutPersistingIt() {
        AuthenticatedTestUser user = registerUser();
        createBill(user, LocalDate.now().minusDays(3), null);

        List<BillResponse> result = user.authenticate(client.get().uri("/bills?status=OVERDUE"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<BillResponse>>() {
                })
                .returnResult()
                .getResponseBody();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(BillStatus.OVERDUE);
    }

    @Test
    void getBillFromAnotherHousehold_returns404() {
        AuthenticatedTestUser owner = registerUser();
        UUID billId = createBill(owner, LocalDate.now().plusDays(5), null).getResponseBody().id();
        AuthenticatedTestUser intruder = registerUser();

        intruder.authenticate(client.get().uri("/bills/" + billId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateBill_changesDescriptionAndAmount() {
        AuthenticatedTestUser user = registerUser();
        UUID billId = createBill(user, LocalDate.now().plusDays(5), null).getResponseBody().id();

        EntityExchangeResult<BillResponse> result = client.put().uri("/bills/" + billId)
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UpdateBillRequest(
                        "Aluguel Reajustado", new BigDecimal("1600.00"), LocalDate.now().plusDays(6), null, null, null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(BillResponse.class)
                .returnResult();

        assertThat(result.getResponseBody().description()).isEqualTo("Aluguel Reajustado");
        assertThat(result.getResponseBody().amount()).isEqualByComparingTo("1600.00");
    }

    @Test
    void payBill_createsRealTransactionAndMarksBillPaid() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        UUID categoryId = createCategory(user);
        UUID billId = client.post().uri("/bills")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateBillRequest("Aluguel", new BigDecimal("1500.00"), LocalDate.now().plusDays(5), null, categoryId, null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(BillResponse.class)
                .returnResult()
                .getResponseBody()
                .id();

        EntityExchangeResult<BillResponse> payResult = client.post().uri("/bills/" + billId + "/pay")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PayBillRequest(accountId, LocalDate.now(), null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(BillResponse.class)
                .returnResult();

        BillResponse bill = payResult.getResponseBody();
        assertThat(bill.status()).isEqualTo(BillStatus.PAID);
        assertThat(bill.paidTransactionId()).isNotNull();

        TransactionResponse transaction = user.authenticate(client.get().uri("/transactions/" + bill.paidTransactionId()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TransactionResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(transaction.accountId()).isEqualTo(accountId);
        assertThat(transaction.categoryId()).isEqualTo(categoryId);
        assertThat(transaction.amount()).isEqualByComparingTo("1500.00");
    }

    @Test
    void payBillWithoutAnyAccount_returns400() {
        AuthenticatedTestUser user = registerUser();
        UUID billId = createBill(user, LocalDate.now().plusDays(5), null).getResponseBody().id();

        client.post().uri("/bills/" + billId + "/pay")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PayBillRequest(null, LocalDate.now(), null))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void payBill_usesSourceAccountIdAsDefaultWhenAccountIdOmitted() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        UUID billId = createBill(user, LocalDate.now().plusDays(5), accountId).getResponseBody().id();

        BillResponse bill = client.post().uri("/bills/" + billId + "/pay")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PayBillRequest(null, LocalDate.now(), null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(BillResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(bill.status()).isEqualTo(BillStatus.PAID);
    }

    @Test
    void payAlreadyPaidBill_returns409() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        UUID billId = createBill(user, LocalDate.now().plusDays(5), accountId).getResponseBody().id();
        client.post().uri("/bills/" + billId + "/pay")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PayBillRequest(null, LocalDate.now(), null))
                .exchange()
                .expectStatus().isOk();

        client.post().uri("/bills/" + billId + "/pay")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PayBillRequest(null, LocalDate.now(), null))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancelBill_setsStatusCanceled() {
        AuthenticatedTestUser user = registerUser();
        UUID billId = createBill(user, LocalDate.now().plusDays(5), null).getResponseBody().id();

        user.authenticate(client.delete().uri("/bills/" + billId))
                .exchange()
                .expectStatus().isNoContent();

        BillResponse bill = user.authenticate(client.get().uri("/bills/" + billId))
                .exchange()
                .expectStatus().isOk()
                .expectBody(BillResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(bill.status()).isEqualTo(BillStatus.CANCELED);
    }

    @Test
    void cancelBillFromAnotherHousehold_returns404() {
        AuthenticatedTestUser owner = registerUser();
        UUID billId = createBill(owner, LocalDate.now().plusDays(5), null).getResponseBody().id();
        AuthenticatedTestUser intruder = registerUser();

        intruder.authenticate(client.delete().uri("/bills/" + billId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }
}
