package com.mecfin.bankprovider;

import static org.assertj.core.api.Assertions.assertThat;

import com.mecfin.bankprovider.application.BankConnectionView;
import com.mecfin.bankprovider.application.SyncResult;
import com.mecfin.bankprovider.domain.BankAccountLinkMode;
import com.mecfin.bankprovider.domain.BankConnectionStatus;
import com.mecfin.dashboard.api.DashboardResponse;
import com.mecfin.shared.domain.RecurrenceRule;
import com.mecfin.shared.web.PagedResponse;
import com.mecfin.testsupport.AuthTestSupport;
import com.mecfin.testsupport.AuthTestSupport.AuthenticatedTestUser;
import com.mecfin.transaction.api.CreateTransactionRequest;
import com.mecfin.transaction.api.TransactionResponse;
import com.mecfin.transaction.domain.TransactionStatus;
import com.mecfin.transaction.domain.TransactionType;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Fase 16: Open Finance ponta a ponta contra um Pluggy falso. */
@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenFinanceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    static final FakePluggy pluggy;

    static {
        try {
            pluggy = new FakePluggy();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String WEBHOOK_SECRET = "segredo-do-webhook-de-teste-0123456789";

    @DynamicPropertySource
    static void pluggyProperties(DynamicPropertyRegistry registry) {
        registry.add("mecfin.pluggy.base-url", pluggy::baseUrl);
        registry.add("mecfin.pluggy.client-id", () -> "client-id-teste");
        registry.add("mecfin.pluggy.client-secret", () -> "client-secret-teste");
        registry.add("mecfin.pluggy.webhook-secret", () -> WEBHOOK_SECRET);
    }

    @AfterAll
    static void stopPluggy() {
        pluggy.stop();
    }

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbc;

    private AuthenticatedTestUser registerUser() {
        return AuthTestSupport.registerAndLogin(client, "user-" + UUID.randomUUID() + "@example.com", "s3cret1234");
    }

    private RestTestClient.RequestBodySpec json(AuthenticatedTestUser user, RestTestClient.RequestBodySpec spec) {
        return spec.cookie("JSESSIONID", user.sessionCookie())
                .cookie("XSRF-TOKEN", user.csrfToken())
                .header("X-XSRF-TOKEN", user.csrfToken())
                .contentType(MediaType.APPLICATION_JSON);
    }

    private String householdOf(AuthenticatedTestUser user) {
        return jdbc.queryForObject("SELECT household_id FROM household_members WHERE user_id = ?", UUID.class,
                user.userId()).toString();
    }

    /** O banco "conectado pelo widget": item carimbado com o household do usuário. */
    private String connectedItem(AuthenticatedTestUser user, String balance, FakePluggy.Tx... txs) {
        String itemId = "item-" + UUID.randomUUID();
        String accountId = "acc-" + UUID.randomUUID();
        pluggy.itemOwner.put(itemId, householdOf(user));
        pluggy.addAccount(itemId, accountId, "Conta Corrente", balance);
        for (FakePluggy.Tx tx : txs) {
            pluggy.addTransaction(accountId, tx);
        }
        return itemId;
    }

    private BankConnectionView register(AuthenticatedTestUser user, String itemId) {
        return json(user, client.post().uri("/api/bank-connections"))
                .body(Map.of("itemId", itemId))
                .exchange().expectStatus().isCreated()
                .expectBody(BankConnectionView.class).returnResult().getResponseBody();
    }

    private BankConnectionView setup(AuthenticatedTestUser user, BankConnectionView connection, Map<String, Object> body) {
        return json(user, client.put().uri("/api/bank-connections/" + connection.id() + "/accounts/"
                        + connection.accounts().get(0).id()))
                .body(body)
                .exchange().expectStatus().isOk()
                .expectBody(BankConnectionView.class).returnResult().getResponseBody();
    }

    private SyncResult sync(AuthenticatedTestUser user, UUID connectionId) {
        return json(user, client.post().uri("/api/bank-connections/" + connectionId + "/sync"))
                .exchange().expectStatus().isOk()
                .expectBody(SyncResult.class).returnResult().getResponseBody();
    }

    private List<TransactionResponse> transactions(AuthenticatedTestUser user) {
        return user.authenticate(client.get().uri("/api/transactions?size=100"))
                .exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<PagedResponse<TransactionResponse>>() {
                })
                .returnResult().getResponseBody().content();
    }

    private static String day(int offset) {
        return LocalDate.now().plusDays(offset).toString();
    }

    @Test
    void connectTokenCarriesTheHouseholdAsClientUserId() {
        AuthenticatedTestUser user = registerUser();

        String token = json(user, client.post().uri("/api/bank-connections/connect-token"))
                .exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, String>>() {
                })
                .returnResult().getResponseBody().get("accessToken");

        assertThat(token).isEqualTo("connect-token-abc");
        assertThat(pluggy.connectTokenBodies.getLast()).contains("\"clientUserId\":\"" + householdOf(user) + "\"");
    }

    @Test
    void itemOfAnotherHouseholdCannotBeRegistered() {
        AuthenticatedTestUser owner = registerUser();
        String itemId = connectedItem(owner, "100.00");
        AuthenticatedTestUser attacker = registerUser();

        json(attacker, client.post().uri("/api/bank-connections"))
                .body(Map.of("itemId", itemId))
                .exchange().expectStatus().isBadRequest();
        json(attacker, client.post().uri("/api/bank-connections"))
                .body(Map.of("itemId", "item-que-nao-existe"))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void fullSyncFollowsTheCursorMatchesTheBankBalanceAndIsIdempotent() {
        AuthenticatedTestUser user = registerUser();
        String itemId = connectedItem(user, "1500.00",
                new FakePluggy.Tx("t1", day(-20), "-120.50", "COMPRA CARTAO PADARIA", "POSTED"),
                new FakePluggy.Tx("t2", day(-15), "3000.00", "SALARIO EMPRESA X", "POSTED"),
                new FakePluggy.Tx("t3", day(-10), "-79.90", "NETFLIX.COM", "POSTED"),
                new FakePluggy.Tx("t4", day(-1), "-45.00", "UBER TRIP", "PENDING"));

        BankConnectionView connection = register(user, itemId);
        assertThat(connection.institutionName()).isEqualTo("Pluggy Bank");
        assertThat(connection.institutionColor()).isEqualTo("#ef294b");
        assertThat(connection.accounts()).singleElement()
                .satisfies(a -> assertThat(a.mode()).isEqualTo(BankAccountLinkMode.PENDING));

        BankConnectionView configured = setup(user, connection, Map.of("mode", "CREATE"));
        UUID accountId = configured.accounts().get(0).accountId();
        assertThat(accountId).isNotNull();

        SyncResult first = sync(user, connection.id());
        // 3 liquidadas em 2 páginas; a pendente fica de fora (pode mudar de id ao liquidar)
        assertThat(first.created()).isEqualTo(3);
        assertThat(transactions(user)).extracting(TransactionResponse::description)
                .containsExactlyInAnyOrder("COMPRA CARTAO PADARIA", "SALARIO EMPRESA X", "NETFLIX.COM");

        // saldo do fin-mec = saldo do banco (saldo inicial calculado a partir do histórico)
        DashboardResponse dashboard = user.authenticate(client.get().uri("/api/dashboard"))
                .exchange().expectStatus().isOk()
                .expectBody(DashboardResponse.class).returnResult().getResponseBody();
        assertThat(dashboard.accountBalances()).filteredOn(b -> b.accountId().equals(accountId)).singleElement()
                .satisfies(b -> assertThat(b.ledgerBalance()).isEqualByComparingTo("1500.00"));

        assertThat(sync(user, connection.id()).created()).isZero();
        assertThat(transactions(user)).hasSize(3);
    }

    @Test
    void bankTransactionConfirmsTheRecurringPrediction() {
        AuthenticatedTestUser user = registerUser();
        String itemId = connectedItem(user, "0");
        BankConnectionView connection = register(user, itemId);
        UUID accountId = setup(user, connection, Map.of("mode", "CREATE")).accounts().get(0).accountId();
        // 2ª ocorrência daqui a ~5 dias (fim de mês pode encurtar até 3): futura = PENDING, e o débito
        // de hoje fica dentro da janela de match de 7 dias
        LocalDate start = LocalDate.now().plusDays(5).minusMonths(1);
        json(user, client.post().uri("/api/transactions"))
                .body(new CreateTransactionRequest(accountId, null, TransactionType.EXPENSE, new BigDecimal("89.90"),
                        "Internet", start, YearMonth.from(start), TransactionStatus.POSTED, RecurrenceRule.MONTHLY))
                .exchange().expectStatus().isCreated();
        TransactionResponse pending = transactions(user).stream()
                .filter(t -> t.transactionDate().equals(start.plusMonths(1))).findFirst().orElseThrow();
        assertThat(pending.status()).isEqualTo(TransactionStatus.PENDING);

        String externalAccount = pluggy.accountsByItem.get(itemId).get(0).get("id");
        pluggy.addTransaction(externalAccount,
                new FakePluggy.Tx("net-1", day(0), "-91.40", "VIVO FIBRA", "POSTED"));

        SyncResult result = sync(user, connection.id());

        assertThat(result.matched()).isEqualTo(1);
        assertThat(result.created()).isZero();
        TransactionResponse confirmed = user.authenticate(client.get().uri("/api/transactions/" + pending.id()))
                .exchange().expectStatus().isOk()
                .expectBody(TransactionResponse.class).returnResult().getResponseBody();
        assertThat(confirmed.status()).isEqualTo(TransactionStatus.POSTED);
        assertThat(confirmed.amount()).isEqualByComparingTo("91.40");
    }

    @Test
    void webhookNeedsTheSecretAndTriggersABackgroundSync() throws InterruptedException {
        AuthenticatedTestUser user = registerUser();
        String itemId = connectedItem(user, "10.00", new FakePluggy.Tx("w1", day(-3), "-5.00", "CAFE", "POSTED"));
        BankConnectionView connection = register(user, itemId);
        setup(user, connection, Map.of("mode", "CREATE"));

        client.post().uri("/api/webhooks/pluggy/segredo-errado").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("event", "transactions/created", "itemId", itemId))
                .exchange().expectStatus().isNotFound();
        client.post().uri("/api/webhooks/pluggy/" + WEBHOOK_SECRET).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("event", "transactions/created", "itemId", itemId, "transactionIds", List.of("forjado")))
                .exchange().expectStatus().isAccepted();

        for (int i = 0; i < 50 && transactions(user).isEmpty(); i++) {
            Thread.sleep(100);
        }
        assertThat(transactions(user)).singleElement()
                .extracting(TransactionResponse::description).isEqualTo("CAFE");
    }

    @Test
    void reconnectTokenPointsTheWidgetAtTheExistingItem() {
        AuthenticatedTestUser user = registerUser();
        String itemId = connectedItem(user, "10.00");
        BankConnectionView connection = register(user, itemId);

        Map<String, String> token = json(user, client.post().uri("/api/bank-connections/connect-token"))
                .body(Map.of("connectionId", connection.id()))
                .exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, String>>() {
                })
                .returnResult().getResponseBody();

        assertThat(token).containsEntry("itemId", itemId);
        assertThat(pluggy.connectTokenBodies.getLast()).contains("\"itemId\":\"" + itemId + "\"");
    }

    @Test
    void itemThatNeedsReconnectionIsFlaggedNotSynced() {
        AuthenticatedTestUser user = registerUser();
        String itemId = connectedItem(user, "10.00", new FakePluggy.Tx("r1", day(-3), "-5.00", "CAFE", "POSTED"));
        BankConnectionView connection = register(user, itemId);
        setup(user, connection, Map.of("mode", "CREATE"));
        pluggy.itemStatus.put(itemId, "LOGIN_ERROR");

        SyncResult result = sync(user, connection.id());

        assertThat(result.status()).isEqualTo(BankConnectionStatus.EXPIRED);
        assertThat(transactions(user)).isEmpty();
        List<BankConnectionView> list = user.authenticate(client.get().uri("/api/bank-connections"))
                .exchange().expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<BankConnectionView>>() {
                })
                .returnResult().getResponseBody();
        assertThat(list).singleElement().satisfies(c -> {
            assertThat(c.status()).isEqualTo(BankConnectionStatus.EXPIRED);
            assertThat(c.lastError()).contains("reconecte");
        });
    }

    @Test
    void connectionsAreScopedDisconnectRemovesTheItemAndOutagesAre502() {
        AuthenticatedTestUser owner = registerUser();
        String itemId = connectedItem(owner, "10.00");
        BankConnectionView connection = register(owner, itemId);
        AuthenticatedTestUser intruder = registerUser();

        json(intruder, client.post().uri("/api/bank-connections/" + connection.id() + "/sync"))
                .exchange().expectStatus().isNotFound();
        intruder.authenticate(client.delete().uri("/api/bank-connections/" + connection.id()))
                .exchange().expectStatus().isNotFound();

        owner.authenticate(client.delete().uri("/api/bank-connections/" + connection.id()))
                .exchange().expectStatus().isNoContent();
        assertThat(pluggy.deletedItems).contains(itemId);
        json(owner, client.post().uri("/api/bank-connections/connect-token"))
                .body(Map.of("connectionId", connection.id()))
                .exchange().expectStatus().isNotFound();

        pluggy.down = true;
        try {
            json(owner, client.post().uri("/api/bank-connections"))
                    .body(Map.of("itemId", connectedItem(owner, "1.00")))
                    .exchange().expectStatus().isEqualTo(502);
        } finally {
            pluggy.down = false;
        }
    }
}
