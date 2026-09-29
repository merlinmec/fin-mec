package com.mecfin.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.account.api.AccountResponse;
import com.mecfin.account.api.CreateAccountRequest;
import com.mecfin.account.domain.AccountType;
import com.mecfin.household.infra.HouseholdDataExporter;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
import com.mecfin.transaction.api.AttachmentController.AttachmentResponse;
import com.mecfin.transaction.api.CreateTransactionRequest;
import com.mecfin.transaction.api.TransactionResponse;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Fase 20: comprovantes no lançamento e exportação LGPD. */
@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AttachmentAndExportIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final String PASSWORD = "s3cret1234";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H'};
    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbc;

    private AuthenticatedTestUser newUser() {
        return AuthTestSupport.registerAndLogin(client, "user-" + UUID.randomUUID() + "@example.com", PASSWORD);
    }

    private RestTestClient.RequestBodySpec post(AuthenticatedTestUser user, String uri) {
        return client.post().uri(uri)
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken());
    }

    private UUID account(AuthenticatedTestUser user) {
        return post(user, "/api/accounts").contentType(MediaType.APPLICATION_JSON)
                .body(new CreateAccountRequest("Conta", AccountType.CHECKING, BigDecimal.TEN))
                .exchange().expectStatus().isCreated()
                .expectBody(AccountResponse.class).returnResult().getResponseBody().id();
    }

    private UUID transaction(AuthenticatedTestUser user) {
        return post(user, "/api/transactions").contentType(MediaType.APPLICATION_JSON)
                .body(new CreateTransactionRequest(account(user), null, TransactionType.EXPENSE,
                        new BigDecimal("30.00"), "Farmácia", LocalDate.now(), YearMonth.now(),
                        TransactionStatus.POSTED, null))
                .exchange().expectStatus().isCreated()
                .expectBody(TransactionResponse.class).returnResult().getResponseBody().id();
    }

    private static LinkedMultiValueMap<String, Object> file(String name, byte[] content) {
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return name;
            }
        });
        return form;
    }

    private RestTestClient.ResponseSpec upload(AuthenticatedTestUser user, UUID tx, String name, byte[] content) {
        return post(user, "/api/transactions/" + tx + "/attachments").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(file(name, content)).exchange();
    }

    @Test
    void attachmentRoundTripIsSniffedScopedAndServedAsDownload() {
        AuthenticatedTestUser user = newUser();
        UUID tx = transaction(user);

        // "exe" que na verdade é PDF: vale o conteúdo, e o nome perde o caminho e ganha a extensão real
        AttachmentResponse pdf = upload(user, tx, "../../nota fiscal.exe", PDF).expectStatus().isCreated()
                .expectBody(AttachmentResponse.class).returnResult().getResponseBody();
        assertThat(pdf.fileName()).isEqualTo("nota fiscal.pdf");
        assertThat(pdf.contentType()).isEqualTo("application/pdf");
        // HTML disfarçado de imagem não passa
        upload(user, tx, "foto.png", "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))
                .expectStatus().isBadRequest();
        upload(user, tx, "grande.png", new byte[5 * 1024 * 1024 + 1]).expectStatus().isEqualTo(413);

        TransactionResponse withCount = user.authenticate(client.get().uri("/api/transactions/" + tx))
                .exchange().expectStatus().isOk()
                .expectBody(TransactionResponse.class).returnResult().getResponseBody();
        assertThat(withCount.attachmentCount()).isEqualTo(1);

        byte[] body = user.authenticate(client.get().uri("/api/transactions/" + tx + "/attachments/" + pdf.id()))
                .exchange().expectStatus().isOk()
                .expectHeader().valueMatches("Content-Disposition", "attachment;.*")
                .expectHeader().valueMatches("Content-Security-Policy", "sandbox.*")
                .expectBody(byte[].class).returnResult().getResponseBody();
        assertThat(body).isEqualTo(PDF);

        AuthenticatedTestUser stranger = newUser();
        stranger.authenticate(client.get().uri("/api/transactions/" + tx + "/attachments/" + pdf.id()))
                .exchange().expectStatus().isNotFound();
        upload(stranger, tx, "x.png", PNG).expectStatus().isNotFound();

        user.authenticate(client.delete().uri("/api/transactions/" + tx + "/attachments/" + pdf.id()))
                .exchange().expectStatus().isNoContent();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transaction_attachment_contents WHERE attachment_id = ?", Long.class,
                pdf.id())).isZero();
    }

    @Test
    void atMostFiveAttachmentsPerTransaction() {
        AuthenticatedTestUser user = newUser();
        UUID tx = transaction(user);
        for (int i = 0; i < 5; i++) {
            upload(user, tx, "foto" + i + ".png", PNG).expectStatus().isCreated();
        }
        upload(user, tx, "sexta.png", PNG).expectStatus().isEqualTo(409);
    }

    @Test
    void importKeepsItsOwnTwoMegabyteLimit() {
        AuthenticatedTestUser user = newUser();
        post(user, "/api/imports/preview?accountId=" + account(user)).contentType(MediaType.MULTIPART_FORM_DATA)
                .body(file("extrato.csv", new byte[2 * 1024 * 1024 + 1]))
                .exchange().expectStatus().isEqualTo(413);
    }

    @Test
    void exportNeedsThePasswordAndNeverContainsSecrets() {
        AuthenticatedTestUser user = newUser();
        UUID tx = transaction(user);
        upload(user, tx, "recibo.png", PNG).expectStatus().isCreated();

        post(user, "/api/account/export").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("password", "errada-errada")).exchange().expectStatus().isBadRequest();

        String json = post(user, "/api/account/export").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("password", PASSWORD)).exchange().expectStatus().isOk()
                .expectHeader().valueMatches("Content-Disposition", "attachment;.*\\.json.*")
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(json).startsWith("{\"formatVersion\":1")
                .contains(user.email(), "Farmácia", "recibo.png", "\"transactions\":", "\"securityEvents\":");
        assertThat(json).doesNotContain("password_hash", "passwordHash", "security_stamp", "totp_secret",
                "token_hash", "access_token_encrypted", "$2a$", "$argon2");
    }

    @Test
    void wrongPasswordsOnSensitiveActionsAreRateLimited() {
        AuthenticatedTestUser user = newUser();
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            statuses.add(post(user, "/api/account/export").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("password", "chute-" + i)).exchange().returnResult().getStatus().value());
        }
        assertThat(statuses.subList(0, 5)).containsOnly(400);
        assertThat(statuses.get(5)).isEqualTo(429);
    }

    @Test
    void everyHouseholdTableIsExportedOrExplicitlyExcluded() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.columns
                WHERE table_schema = 'public' AND column_name = 'household_id'
                """, String.class);
        assertThat(tables).contains("transaction_attachments");
        for (String table : tables) {
            assertThat(HouseholdDataExporter.exportedTables().contains(table)
                    || HouseholdDataExporter.NOT_EXPORTED.containsKey(table)).as(table).isTrue();
        }
    }
}
