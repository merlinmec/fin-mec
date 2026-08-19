package com.mecfin.creditcard;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.account.api.AccountResponse;
import com.mecfin.account.api.CreateAccountRequest;
import com.mecfin.account.domain.AccountType;
import com.mecfin.category.api.CategoryResponse;
import com.mecfin.category.api.CreateCategoryRequest;
import com.mecfin.category.domain.CategoryType;
import com.mecfin.creditcard.api.CreateCreditCardChargeRequest;
import com.mecfin.creditcard.api.CreateCreditCardRequest;
import com.mecfin.creditcard.api.CreditCardChargeResponse;
import com.mecfin.creditcard.api.CreditCardInvoiceResponse;
import com.mecfin.creditcard.api.CreditCardResponse;
import com.mecfin.creditcard.api.PayCreditCardInvoiceRequest;
import com.mecfin.creditcard.api.UpdateCreditCardRequest;
import com.mecfin.creditcard.domain.CreditCardInvoiceStatus;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
import com.mecfin.transaction.api.TransactionResponse;
import com.mecfin.transaction.domain.TransactionType;
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
class CreditCardIT {

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
                .body(new CreateCategoryRequest("Mercado", CategoryType.EXPENSE, null, null, null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CategoryResponse.class)
                .returnResult()
                .getResponseBody()
                .id();
    }

    private EntityExchangeResult<CreditCardResponse> createCard(AuthenticatedTestUser user, UUID paymentAccountId) {
        return client.post().uri("/credit-cards")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCreditCardRequest("Nubank", new BigDecimal("5000.00"), 10, 17, paymentAccountId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CreditCardResponse.class)
                .returnResult();
    }

    private EntityExchangeResult<List<CreditCardChargeResponse>> registerCharge(
            AuthenticatedTestUser user, UUID cardId, UUID categoryId, LocalDate purchaseDate, Integer installments) {
        return client.post().uri("/credit-cards/" + cardId + "/charges")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCreditCardChargeRequest(
                        "Compra", new BigDecimal("100.00"), TransactionType.EXPENSE, categoryId, purchaseDate,
                        installments))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<List<CreditCardChargeResponse>>() {
                })
                .returnResult();
    }

    private UUID firstInvoiceId(AuthenticatedTestUser user, UUID cardId) {
        return user.authenticate(client.get().uri("/credit-cards/" + cardId + "/invoices"))
                .exchange()
                .expectBody(new ParameterizedTypeReference<List<CreditCardInvoiceResponse>>() {
                })
                .returnResult()
                .getResponseBody()
                .get(0)
                .id();
    }

    private EntityExchangeResult<CreditCardInvoiceResponse> payInvoice(AuthenticatedTestUser user, UUID invoiceId) {
        return client.post().uri("/credit-card-invoices/" + invoiceId + "/pay")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PayCreditCardInvoiceRequest(null, LocalDate.now(), null))
                .exchange()
                .expectBody(CreditCardInvoiceResponse.class)
                .returnResult();
    }

    @Test
    void createCreditCard_returns201ScopedToOwnHousehold() {
        AuthenticatedTestUser user = registerUser();

        CreditCardResponse body = createCard(user, null).getResponseBody();

        assertThat(body.id()).isNotNull();
        assertThat(body.name()).isEqualTo("Nubank");
        assertThat(body.archived()).isFalse();
    }

    @Test
    void createCreditCardWithoutSession_returns401() {
        client.post().uri("/credit-cards")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCreditCardRequest("Nubank", new BigDecimal("5000.00"), 10, 17, null))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void createCreditCardWithInvalidPaymentAccount_returns400() {
        AuthenticatedTestUser user = registerUser();

        client.post().uri("/credit-cards")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCreditCardRequest("Nubank", new BigDecimal("5000.00"), 10, 17, UUID.randomUUID()))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void listCreditCards_returnsOnlyOwnHousehold() {
        AuthenticatedTestUser owner = registerUser();
        createCard(owner, null);
        AuthenticatedTestUser other = registerUser();
        createCard(other, null);

        List<CreditCardResponse> result = owner.authenticate(client.get().uri("/credit-cards"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<CreditCardResponse>>() {
                })
                .returnResult()
                .getResponseBody();

        assertThat(result).hasSize(1);
    }

    @Test
    void getCreditCardFromAnotherHousehold_returns404() {
        AuthenticatedTestUser owner = registerUser();
        UUID cardId = createCard(owner, null).getResponseBody().id();
        AuthenticatedTestUser intruder = registerUser();

        intruder.authenticate(client.get().uri("/credit-cards/" + cardId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateCreditCard_changesNameAndLimit() {
        AuthenticatedTestUser user = registerUser();
        UUID cardId = createCard(user, null).getResponseBody().id();

        CreditCardResponse updated = client.put().uri("/credit-cards/" + cardId)
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UpdateCreditCardRequest("Nubank Ultravioleta", new BigDecimal("8000.00"), 5, 12, null, false))
                .exchange()
                .expectStatus().isOk()
                .expectBody(CreditCardResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated.name()).isEqualTo("Nubank Ultravioleta");
        assertThat(updated.creditLimit()).isEqualByComparingTo("8000.00");
    }

    @Test
    void deleteCreditCard_softDeletesAndHidesFromListAndGet() {
        AuthenticatedTestUser user = registerUser();
        UUID cardId = createCard(user, null).getResponseBody().id();

        user.authenticate(client.delete().uri("/credit-cards/" + cardId))
                .exchange()
                .expectStatus().isNoContent();

        user.authenticate(client.get().uri("/credit-cards/" + cardId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void registerChargeWithInvisibleCategory_returns400() {
        AuthenticatedTestUser user = registerUser();
        UUID cardId = createCard(user, null).getResponseBody().id();

        client.post().uri("/credit-cards/" + cardId + "/charges")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCreditCardChargeRequest(
                        "Compra", BigDecimal.TEN, TransactionType.EXPENSE, UUID.randomUUID(), LocalDate.now(), null))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void registerChargeWithTransferType_returns400() {
        AuthenticatedTestUser user = registerUser();
        UUID cardId = createCard(user, null).getResponseBody().id();

        client.post().uri("/credit-cards/" + cardId + "/charges")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCreditCardChargeRequest(
                        "Compra", BigDecimal.TEN, TransactionType.TRANSFER, null, LocalDate.now(), null))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void registerCharge_singleChargeCreatesOneInvoiceWithDerivedTotal() {
        AuthenticatedTestUser user = registerUser();
        UUID cardId = createCard(user, null).getResponseBody().id();
        UUID categoryId = createCategory(user);

        registerCharge(user, cardId, categoryId, LocalDate.now(), null);

        List<CreditCardInvoiceResponse> invoices = user.authenticate(client.get().uri("/credit-cards/" + cardId + "/invoices"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<CreditCardInvoiceResponse>>() {
                })
                .returnResult()
                .getResponseBody();

        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).totalAmount()).isEqualByComparingTo("100.00");
        assertThat(invoices.get(0).charges()).hasSize(1);
        assertThat(invoices.get(0).status()).isEqualTo(CreditCardInvoiceStatus.OPEN);
    }

    @Test
    void registerCharge_twoChargesSameDateShareSameInvoice() {
        AuthenticatedTestUser user = registerUser();
        UUID cardId = createCard(user, null).getResponseBody().id();

        registerCharge(user, cardId, null, LocalDate.now(), null);
        registerCharge(user, cardId, null, LocalDate.now(), null);

        List<CreditCardInvoiceResponse> invoices = user.authenticate(client.get().uri("/credit-cards/" + cardId + "/invoices"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<CreditCardInvoiceResponse>>() {
                })
                .returnResult()
                .getResponseBody();

        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).totalAmount()).isEqualByComparingTo("200.00");
        assertThat(invoices.get(0).charges()).hasSize(2);
    }

    @Test
    void registerCharge_installmentsSpreadAcrossSeparateInvoices() {
        AuthenticatedTestUser user = registerUser();
        UUID cardId = createCard(user, null).getResponseBody().id();

        List<CreditCardChargeResponse> charges = registerCharge(user, cardId, null, LocalDate.now(), 3).getResponseBody();
        assertThat(charges).hasSize(3);
        assertThat(charges).extracting(CreditCardChargeResponse::installmentTotal).containsExactly(3, 3, 3);

        List<CreditCardInvoiceResponse> invoices = user.authenticate(client.get().uri("/credit-cards/" + cardId + "/invoices"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<CreditCardInvoiceResponse>>() {
                })
                .returnResult()
                .getResponseBody();

        assertThat(invoices).hasSize(3);
        assertThat(invoices).allSatisfy(invoice -> assertThat(invoice.totalAmount()).isEqualByComparingTo("100.00"));
    }

    @Test
    void payInvoice_createsRealTransactionAndMarksInvoicePaid() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        UUID cardId = createCard(user, accountId).getResponseBody().id();
        UUID categoryId = createCategory(user);
        registerCharge(user, cardId, categoryId, LocalDate.now(), null);
        UUID invoiceId = firstInvoiceId(user, cardId);

        CreditCardInvoiceResponse paid = payInvoice(user, invoiceId).getResponseBody();

        assertThat(paid.status()).isEqualTo(CreditCardInvoiceStatus.PAID);
        assertThat(paid.paidTransactionId()).isNotNull();

        TransactionResponse transaction = user.authenticate(client.get().uri("/transactions/" + paid.paidTransactionId()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TransactionResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(transaction.accountId()).isEqualTo(accountId);
        assertThat(transaction.amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void payInvoiceWithoutAnyAccount_returns400() {
        AuthenticatedTestUser user = registerUser();
        UUID cardId = createCard(user, null).getResponseBody().id();
        registerCharge(user, cardId, null, LocalDate.now(), null);
        UUID invoiceId = firstInvoiceId(user, cardId);

        client.post().uri("/credit-card-invoices/" + invoiceId + "/pay")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PayCreditCardInvoiceRequest(null, LocalDate.now(), null))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void payAlreadyPaidInvoice_returns409() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        UUID cardId = createCard(user, accountId).getResponseBody().id();
        registerCharge(user, cardId, null, LocalDate.now(), null);
        UUID invoiceId = firstInvoiceId(user, cardId);
        client.post().uri("/credit-card-invoices/" + invoiceId + "/pay")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PayCreditCardInvoiceRequest(null, LocalDate.now(), null))
                .exchange()
                .expectStatus().isOk();

        client.post().uri("/credit-card-invoices/" + invoiceId + "/pay")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PayCreditCardInvoiceRequest(null, LocalDate.now(), null))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void deleteCharge_onOpenInvoiceRemovesItFromTotal() {
        AuthenticatedTestUser user = registerUser();
        UUID cardId = createCard(user, null).getResponseBody().id();
        UUID chargeId = registerCharge(user, cardId, null, LocalDate.now(), null).getResponseBody().get(0).id();
        UUID invoiceId = firstInvoiceId(user, cardId);

        user.authenticate(client.delete().uri("/credit-card-invoices/" + invoiceId + "/charges/" + chargeId))
                .exchange()
                .expectStatus().isNoContent();

        CreditCardInvoiceResponse invoice = user.authenticate(client.get().uri("/credit-card-invoices/" + invoiceId))
                .exchange()
                .expectStatus().isOk()
                .expectBody(CreditCardInvoiceResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(invoice.charges()).isEmpty();
        assertThat(invoice.totalAmount()).isEqualByComparingTo("0");
    }

    @Test
    void getInvoiceFromAnotherHousehold_returns404() {
        AuthenticatedTestUser owner = registerUser();
        UUID cardId = createCard(owner, null).getResponseBody().id();
        registerCharge(owner, cardId, null, LocalDate.now(), null);
        UUID invoiceId = firstInvoiceId(owner, cardId);
        AuthenticatedTestUser intruder = registerUser();

        intruder.authenticate(client.get().uri("/credit-card-invoices/" + invoiceId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }
}
