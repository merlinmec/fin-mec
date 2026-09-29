package com.mecfin.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.account.api.AccountResponse;
import com.mecfin.account.api.CreateAccountRequest;
import com.mecfin.account.domain.AccountType;
import com.mecfin.category.api.CategoryResponse;
import com.mecfin.category.api.CreateCategoryRequest;
import com.mecfin.category.domain.CategoryType;
import com.mecfin.importing.api.CommitImportRequest;
import com.mecfin.importing.application.CommitRow;
import com.mecfin.importing.application.ImportPreview;
import com.mecfin.importing.application.ImportResult;
import com.mecfin.importing.application.PreviewRow;
import com.mecfin.importing.application.PreviewStatus;
import com.mecfin.importing.domain.ImportFormat;
import com.mecfin.shared.domain.RecurrenceRule;
import com.mecfin.shared.web.PagedResponse;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
import com.mecfin.transaction.api.CreateTransactionRequest;
import com.mecfin.transaction.api.TransactionResponse;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Fase 15: importação de extrato ponta a ponta. */
@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ImportIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final DateTimeFormatter OFX_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    @Autowired
    private RestTestClient client;

    private AuthenticatedTestUser registerUser() {
        return AuthTestSupport.registerAndLogin(client, "user-" + UUID.randomUUID() + "@example.com", "s3cret1234");
    }

    private RestTestClient.RequestBodySpec json(AuthenticatedTestUser user, RestTestClient.RequestBodySpec spec) {
        return spec.cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private UUID createAccount(AuthenticatedTestUser user) {
        return json(user, client.post().uri("/api/accounts"))
                .body(new CreateAccountRequest("Corrente", AccountType.CHECKING, BigDecimal.ZERO))
                .exchange().expectStatus().isCreated()
                .expectBody(AccountResponse.class).returnResult().getResponseBody().id();
    }

    private UUID createCategory(AuthenticatedTestUser user, String name, CategoryType type) {
        return json(user, client.post().uri("/api/categories"))
                .body(new CreateCategoryRequest(name, type, null, "#16a34a", "home"))
                .exchange().expectStatus().isCreated()
                .expectBody(CategoryResponse.class).returnResult().getResponseBody().id();
    }

    private static String ofx(Object[]... transactions) {
        StringBuilder sb = new StringBuilder("OFXHEADER:100\nDATA:OFXSGML\nCHARSET:1252\n\n<OFX><BANKTRANLIST>\n");
        for (Object[] t : transactions) {
            sb.append("<STMTTRN>\n<DTPOSTED>").append(((LocalDate) t[0]).format(OFX_DATE))
                    .append("\n<TRNAMT>").append(t[1])
                    .append("\n<FITID>").append(t[2])
                    .append("\n<MEMO>").append(t[3])
                    .append("\n</STMTTRN>\n");
        }
        return sb.append("</BANKTRANLIST></OFX>\n").toString();
    }

    private RestTestClient.ResponseSpec upload(AuthenticatedTestUser user, UUID accountId, String fileName, byte[] content) {
        // O FormHttpMessageConverter usa getFilename() do Resource como filename da parte.
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        return client.post().uri("/api/imports/preview?accountId=" + accountId)
                .cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .exchange();
    }

    private ImportPreview preview(AuthenticatedTestUser user, UUID accountId, String content) {
        return upload(user, accountId, "extrato.ofx", content.getBytes(StandardCharsets.UTF_8))
                .expectStatus().isOk()
                .expectBody(ImportPreview.class).returnResult().getResponseBody();
    }

    /** Aceita a sugestão da pré-visualização para toda linha, como o botão "Importar" da UI. */
    private ImportResult commitAsSuggested(AuthenticatedTestUser user, UUID accountId, ImportPreview preview) {
        List<CommitImportRequest.Row> rows = preview.rows().stream().map(row -> new CommitImportRequest.Row(
                row.externalId(), row.date(), row.description(),
                row.type() == TransactionType.EXPENSE ? row.amount().negate() : row.amount(),
                switch (row.status()) {
                    case NEW -> CommitRow.Action.CREATE;
                    case MATCHES_PENDING -> CommitRow.Action.MATCH;
                    default -> CommitRow.Action.SKIP;
                },
                row.suggestedCategoryId(), null, row.matchTransactionId())).toList();
        return json(user, client.post().uri("/api/imports"))
                .body(new CommitImportRequest(accountId, "extrato.ofx", preview.format(), rows))
                .exchange().expectStatus().isCreated()
                .expectBody(ImportResult.class).returnResult().getResponseBody();
    }

    private List<TransactionResponse> transactions(AuthenticatedTestUser user) {
        return user.authenticate(client.get().uri("/api/transactions?size=100"))
                .exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<PagedResponse<TransactionResponse>>() {
                })
                .returnResult().getResponseBody().content();
    }

    @Test
    void importingTheSameFileTwiceNeverDuplicates() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        LocalDate day = LocalDate.now().minusDays(5);
        String file = ofx(new Object[] {day, "-45.90", "F1", "PADARIA SAO JOAO"},
                new Object[] {day, "-45.90", "F2", "PADARIA SAO JOAO"},
                new Object[] {day.plusDays(1), "1500.00", "F3", "PIX RECEBIDO"});

        ImportPreview first = preview(user, accountId, file);
        assertThat(first.format()).isEqualTo(ImportFormat.OFX);
        assertThat(first.newCount()).isEqualTo(3);
        ImportResult result = commitAsSuggested(user, accountId, first);
        assertThat(result.created()).isEqualTo(3);

        ImportPreview second = preview(user, accountId, file);
        assertThat(second.alreadyImportedCount()).isEqualTo(3);
        assertThat(second.newCount()).isZero();

        // mesmo reenviando as linhas como CREATE (aba antiga, requisição repetida), nada é criado
        List<CommitImportRequest.Row> forced = first.rows().stream().map(row -> new CommitImportRequest.Row(
                row.externalId(), row.date(), row.description(), row.amount(), CommitRow.Action.CREATE, null, null,
                null)).toList();
        ImportResult replay = json(user, client.post().uri("/api/imports"))
                .body(new CommitImportRequest(accountId, "x.ofx", ImportFormat.OFX, forced))
                .exchange().expectStatus().isCreated()
                .expectBody(ImportResult.class).returnResult().getResponseBody();
        assertThat(replay.created()).isZero();
        assertThat(replay.skipped()).isEqualTo(3);
        assertThat(transactions(user)).hasSize(3);
    }

    @Test
    void statementLineConfirmsThePendingOccurrenceOfARecurringTransaction() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        LocalDate start = LocalDate.now().minusDays(1);
        json(user, client.post().uri("/api/transactions"))
                .body(new CreateTransactionRequest(accountId, null, TransactionType.INCOME, new BigDecimal("8200.00"),
                        "Salário", start, YearMonth.from(start), null, RecurrenceRule.MONTHLY))
                .exchange().expectStatus().isCreated();
        LocalDate nextOccurrence = start.plusMonths(1);
        TransactionResponse pending = transactions(user).stream()
                .filter(t -> t.transactionDate().equals(nextOccurrence)).findFirst().orElseThrow();
        assertThat(pending.status()).isEqualTo(TransactionStatus.PENDING);

        // o banco pagou 2 dias depois e com centavos a mais
        ImportPreview preview = preview(user, accountId,
                ofx(new Object[] {nextOccurrence.plusDays(2), "8210.35", "SAL-1", "SALARIO EMPRESA X"}));

        PreviewRow row = preview.rows().get(0);
        assertThat(row.status()).isEqualTo(PreviewStatus.MATCHES_PENDING);
        assertThat(row.matchTransactionId()).isEqualTo(pending.id());

        ImportResult result = commitAsSuggested(user, accountId, preview);
        assertThat(result.matched()).isEqualTo(1);
        assertThat(result.created()).isZero();

        TransactionResponse confirmed = user.authenticate(client.get().uri("/api/transactions/" + pending.id()))
                .exchange().expectStatus().isOk()
                .expectBody(TransactionResponse.class).returnResult().getResponseBody();
        assertThat(confirmed.status()).isEqualTo(TransactionStatus.POSTED);
        assertThat(confirmed.amount()).isEqualByComparingTo("8210.35");
        assertThat(confirmed.transactionDate()).isEqualTo(nextOccurrence.plusDays(2));
        // e a mesma linha não casa de novo numa reimportação
        assertThat(preview(user, accountId,
                ofx(new Object[] {nextOccurrence.plusDays(2), "8210.35", "SAL-1", "SALARIO EMPRESA X"}))
                .alreadyImportedCount()).isEqualTo(1);
    }

    @Test
    void manualEntryWithSameDateAndAmountIsFlaggedAsPossibleDuplicate() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        LocalDate day = LocalDate.now().minusDays(2);
        json(user, client.post().uri("/api/transactions"))
                .body(new CreateTransactionRequest(accountId, null, TransactionType.EXPENSE, new BigDecimal("120.00"),
                        "Internet (lancei na mão)", day, YearMonth.from(day), null, null))
                .exchange().expectStatus().isCreated();

        ImportPreview preview = preview(user, accountId, ofx(new Object[] {day, "-120.00", "NET-1", "DEB AUT VIVO FIBRA"}));

        assertThat(preview.rows().get(0).status()).isEqualTo(PreviewStatus.POSSIBLE_DUPLICATE);
        assertThat(preview.rows().get(0).matchDescription()).isEqualTo("Internet (lancei na mão)");
    }

    @Test
    void categoryIsSuggestedByRuleFirstAndByHistoryOtherwise() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        UUID transport = createCategory(user, "Transporte app", CategoryType.EXPENSE);
        UUID bakery = createCategory(user, "Padaria", CategoryType.EXPENSE);
        json(user, client.post().uri("/api/categorization-rules"))
                .body(Map.of("pattern", "Uber", "categoryId", transport))
                .exchange().expectStatus().isCreated();
        LocalDate old = LocalDate.now().minusMonths(1);
        json(user, client.post().uri("/api/transactions"))
                .body(new CreateTransactionRequest(accountId, bakery, TransactionType.EXPENSE, new BigDecimal("9.00"),
                        "COMPRA CARTAO 1111 PADARIA REAL", old, YearMonth.from(old), null, null))
                .exchange().expectStatus().isCreated();

        LocalDate day = LocalDate.now().minusDays(1);
        ImportPreview preview = preview(user, accountId, ofx(
                new Object[] {day, "-23.10", "U1", "UBER *TRIP HELP.UBER.COM"},
                new Object[] {day, "-12.00", "P1", "Compra Cartao 2222 Padaria Real"},
                new Object[] {day, "-5.00", "X1", "ALGO NUNCA VISTO"}));

        assertThat(preview.rows()).extracting(PreviewRow::suggestedCategoryId, PreviewRow::suggestionSource)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(transport, "RULE"),
                        org.assertj.core.groups.Tuple.tuple(bakery, "HISTORY"),
                        org.assertj.core.groups.Tuple.tuple(null, null));

        json(user, client.post().uri("/api/categorization-rules"))
                .body(Map.of("pattern", "UBER", "categoryId", transport))
                .exchange().expectStatus().isEqualTo(409);
    }

    @Test
    void undoCancelsWhatTheImportCreatedAndAllowsReimporting() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        String file = ofx(new Object[] {LocalDate.now().minusDays(3), "-60.00", "M1", "MERCADO"});
        ImportResult result = commitAsSuggested(user, accountId, preview(user, accountId, file));

        json(user, client.post().uri("/api/imports/" + result.batchId() + "/undo"))
                .exchange().expectStatus().isOk();

        assertThat(transactions(user)).singleElement()
                .extracting(TransactionResponse::status).isEqualTo(TransactionStatus.CANCELED);
        json(user, client.post().uri("/api/imports/" + result.batchId() + "/undo"))
                .exchange().expectStatus().isEqualTo(409);
        assertThat(preview(user, accountId, file).newCount()).isEqualTo(1);
    }

    @Test
    void csvIsDetectedAndParsed() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);
        String csv = "Data;Descrição;Valor\n" + LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + ";Farmácia;-32,50\n";

        ImportPreview preview = upload(user, accountId, "extrato.csv", csv.getBytes(StandardCharsets.UTF_8))
                .expectStatus().isOk().expectBody(ImportPreview.class).returnResult().getResponseBody();

        assertThat(preview.format()).isEqualTo(ImportFormat.CSV);
        assertThat(preview.columns()).containsExactly("Data", "Descrição", "Valor");
        assertThat(preview.rows()).singleElement().satisfies(row -> {
            assertThat(row.amount()).isEqualByComparingTo("32.50");
            assertThat(row.type()).isEqualTo(TransactionType.EXPENSE);
        });
    }

    @Test
    void badFilesAreRejectedWithoutTouchingData() {
        AuthenticatedTestUser user = registerUser();
        UUID accountId = createAccount(user);

        upload(user, accountId, "extrato.pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8)).expectStatus().isBadRequest();
        upload(user, accountId, "extrato.csv", new byte[] {'a', 0, 'b'}).expectStatus().isBadRequest();
        upload(user, accountId, "extrato.csv", "so;um;cabecalho\n".getBytes(StandardCharsets.UTF_8))
                .expectStatus().isBadRequest();
        upload(user, accountId, "grande.csv", new byte[3 * 1024 * 1024]).expectStatus().isEqualTo(413);
    }

    @Test
    void anotherHouseholdsAccountAndImportsAreOffLimits() {
        AuthenticatedTestUser owner = registerUser();
        UUID accountId = createAccount(owner);
        ImportResult result = commitAsSuggested(owner, accountId,
                preview(owner, accountId, ofx(new Object[] {LocalDate.now(), "-1.00", "Z1", "CAFE"})));
        AuthenticatedTestUser intruder = registerUser();

        upload(intruder, accountId, "extrato.ofx",
                ofx(new Object[] {LocalDate.now(), "-1.00", "Z2", "CAFE"}).getBytes(StandardCharsets.UTF_8))
                .expectStatus().isBadRequest();
        json(intruder, client.post().uri("/api/imports/" + result.batchId() + "/undo"))
                .exchange().expectStatus().isNotFound();
    }
}
