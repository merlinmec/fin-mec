package com.mecfin.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.bill.api.BillResponse;
import com.mecfin.bill.api.CreateBillRequest;
import com.mecfin.notification.api.NotificationResponse;
import com.mecfin.notification.domain.NotificationType;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
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
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationIT {

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

    private UUID createOverdueBill(AuthenticatedTestUser user) {
        return client.post().uri("/bills")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateBillRequest("Aluguel", new BigDecimal("1500.00"), LocalDate.now().minusDays(2), null, null, null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(BillResponse.class)
                .returnResult()
                .getResponseBody()
                .id();
    }

    private List<NotificationResponse> sync(AuthenticatedTestUser user) {
        return client.post().uri("/notifications/sync")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<NotificationResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }

    @Test
    void syncWithoutSession_returns401() {
        client.post().uri("/notifications/sync")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void sync_createsOverdueNotificationForOpenBillPastDueDate() {
        AuthenticatedTestUser user = registerUser();
        createOverdueBill(user);

        List<NotificationResponse> notifications = sync(user);

        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).type()).isEqualTo(NotificationType.BILL_OVERDUE);
        assertThat(notifications.get(0).read()).isFalse();
    }

    @Test
    void sync_isIdempotentAndDoesNotDuplicateOnSecondCall() {
        AuthenticatedTestUser user = registerUser();
        createOverdueBill(user);

        sync(user);
        List<NotificationResponse> secondSync = sync(user);

        assertThat(secondSync).hasSize(1);
    }

    @Test
    void listReturnsOnlyOwnHousehold() {
        AuthenticatedTestUser owner = registerUser();
        createOverdueBill(owner);
        sync(owner);
        AuthenticatedTestUser other = registerUser();
        createOverdueBill(other);
        sync(other);

        List<NotificationResponse> result = owner.authenticate(client.get().uri("/notifications"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<NotificationResponse>>() {
                })
                .returnResult()
                .getResponseBody();

        assertThat(result).hasSize(1);
    }

    @Test
    void markRead_setsReadTrueAndFiltersFromUnreadList() {
        AuthenticatedTestUser user = registerUser();
        createOverdueBill(user);
        UUID notificationId = sync(user).get(0).id();

        NotificationResponse marked = client.post().uri("/notifications/" + notificationId + "/read")
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody(NotificationResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(marked.read()).isTrue();

        List<NotificationResponse> unread = user.authenticate(client.get().uri("/notifications?read=false"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<NotificationResponse>>() {
                })
                .returnResult()
                .getResponseBody();
        assertThat(unread).isEmpty();
    }

    @Test
    void markReadFromAnotherHousehold_returns404() {
        AuthenticatedTestUser owner = registerUser();
        createOverdueBill(owner);
        UUID notificationId = sync(owner).get(0).id();
        AuthenticatedTestUser intruder = registerUser();

        client.post().uri("/notifications/" + notificationId + "/read")
                .cookie("JSESSIONID", intruder.sessionCookie())
                .cookie("XSRF-TOKEN", intruder.csrfToken())
                .header("X-XSRF-TOKEN", intruder.csrfToken())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }
}
