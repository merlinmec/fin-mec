package com.mecfin.bankprovider.infra;

import com.mecfin.bankprovider.application.BankProviderClient;
import com.mecfin.bankprovider.application.ExternalBankAccount;
import com.mecfin.bankprovider.application.ExternalBankTransaction;
import com.mecfin.bankprovider.application.ExternalItem;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Adapter Pluggy (https://docs.pluggy.ai) da porta BankProviderClient.
 * <ul>
 *   <li>Autenticação: POST /auth (clientId/clientSecret) devolve uma API key de 2 h, enviada no
 *       cabeçalho X-API-KEY. Guardada em memória e renovada antes de vencer — e também na hora,
 *       se a Pluggy responder 401.</li>
 *   <li>Transações: GET /v2/transactions (paginação por cursor). O /transactions antigo está
 *       deprecado até 31/12/2026, então nem é usado.</li>
 *   <li>As URIs são montadas já codificadas (URI, não template): o cursor "next" da Pluggy vem
 *       pronto, e um template o codificaria de novo (%25...).</li>
 * </ul>
 */
@Component
public class PluggyClient implements BankProviderClient {

    private static final Duration KEY_LIFETIME = Duration.ofMinutes(110);
    private static final int MAX_PAGES = 200;
    private static final Pattern ITEM_ID = Pattern.compile("[A-Za-z0-9-]{1,100}");
    private static final Set<String> RECONNECT_STATUSES = Set.of("LOGIN_ERROR", "WAITING_USER_INPUT");
    private static final Set<String> RECONNECT_EXECUTIONS = Set.of("INVALID_CREDENTIALS", "INVALID_CREDENTIALS_MFA",
            "ACCOUNT_NEEDS_ACTION", "USER_AUTHORIZATION_REVOKED", "ACCOUNT_CREDENTIALS_RESET", "ACCOUNT_LOCKED",
            "USER_INPUT_TIMEOUT", "USER_AUTHORIZATION_NOT_GRANTED");

    record AuthResponse(String apiKey) {
    }

    record ConnectTokenResponse(String accessToken) {
    }

    record PluggyConnector(Object id, String name, String imageUrl, String primaryColor) {
    }

    record PluggyError(String code, String message) {
    }

    record PluggyItem(String id, String status, String executionStatus, String clientUserId, PluggyConnector connector,
            PluggyError error) {
    }

    record PluggyAccount(String id, String type, String subtype, String name, String marketingName, String number,
            BigDecimal balance, String currencyCode) {
    }

    record AccountsPage(List<PluggyAccount> results) {
    }

    record PluggyTransaction(String id, String accountId, String description, BigDecimal amount, String date,
            String status) {
    }

    record TransactionsPage(List<PluggyTransaction> results, String next) {
    }

    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final RestClient rest;
    private String apiKey;
    private Instant apiKeyExpiresAt = Instant.EPOCH;

    public PluggyClient(
            @Value("${mecfin.pluggy.base-url}") String baseUrl,
            @Value("${mecfin.pluggy.client-id:}") String clientId,
            @Value("${mecfin.pluggy.client-secret:}") String clientSecret) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String providerName() {
        return "PLUGGY";
    }

    @Override
    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    @Override
    public String createConnectToken(String clientUserId, String existingItemId, String webhookUrl) {
        Map<String, Object> options = new HashMap<>();
        options.put("clientUserId", clientUserId);
        options.put("avoidDuplicates", true);
        if (webhookUrl != null) {
            options.put("webhookUrl", webhookUrl);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("options", options);
        if (existingItemId != null) {
            body.put("itemId", existingItemId);
        }
        return withKey(key -> rest.post().uri(uri("/connect_token", Map.of()))
                .header("X-API-KEY", key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ConnectTokenResponse.class))
                .accessToken();
    }

    @Override
    public ExternalItem getItem(String itemId) {
        URI itemUri = uri(itemPath(itemId), Map.of());
        PluggyItem item = withKey(key -> rest.get().uri(itemUri)
                .header("X-API-KEY", key).retrieve().body(PluggyItem.class));
        PluggyConnector connector = item.connector() != null ? item.connector() : new PluggyConnector(null, "Banco", null, null);
        return new ExternalItem(item.id(), item.clientUserId(), health(item), errorMessage(item),
                connector.id() != null ? String.valueOf(connector.id()) : "unknown",
                connector.name(), connector.imageUrl(), connector.primaryColor());
    }

    @Override
    public List<ExternalBankAccount> getBankAccounts(String itemId) {
        AccountsPage page = withKey(key -> rest.get()
                .uri(uri("/accounts", Map.of("itemId", itemId, "type", "BANK")))
                .header("X-API-KEY", key).retrieve().body(AccountsPage.class));
        List<ExternalBankAccount> accounts = new ArrayList<>();
        for (PluggyAccount a : page.results() == null ? List.<PluggyAccount>of() : page.results()) {
            String name = a.marketingName() != null && !a.marketingName().isBlank() ? a.marketingName() : a.name();
            accounts.add(new ExternalBankAccount(a.id(), name, a.number(), "SAVINGS_ACCOUNT".equals(a.subtype()),
                    a.balance(), a.currencyCode()));
        }
        return accounts;
    }

    @Override
    public List<ExternalBankTransaction> getTransactions(String externalAccountId, LocalDate from) {
        List<ExternalBankTransaction> transactions = new ArrayList<>();
        URI next = uri("/v2/transactions", Map.of("accountId", externalAccountId, "dateFrom", from.toString()));
        for (int page = 0; next != null && page < MAX_PAGES; page++) {
            URI current = next;
            TransactionsPage result = withKey(key -> rest.get().uri(current)
                    .header("X-API-KEY", key).retrieve().body(TransactionsPage.class));
            for (PluggyTransaction t : result.results() == null ? List.<PluggyTransaction>of() : result.results()) {
                if (t.date() == null || t.amount() == null) {
                    continue;
                }
                transactions.add(new ExternalBankTransaction(t.id(), externalAccountId, t.amount(),
                        t.description() == null || t.description().isBlank() ? "Lançamento do banco" : t.description(),
                        LocalDate.parse(t.date().substring(0, 10)), !"PENDING".equals(t.status())));
            }
            next = nextPage(result.next(), externalAccountId);
        }
        return transactions;
    }

    @Override
    public void deleteItem(String itemId) {
        URI itemUri = uri(itemPath(itemId), Map.of());
        withKey(key -> rest.delete().uri(itemUri)
                .header("X-API-KEY", key).retrieve().toBodilessEntity());
    }

    // "next" pode vir como query string ("?accountId=...&after=..."), caminho, URL completa do
    // host da Pluggy ou só o token do cursor — os quatro formatos aparecem em integrações reais.
    URI nextPage(String next, String externalAccountId) {
        if (next == null || next.isBlank()) {
            return null;
        }
        if (next.startsWith("?")) {
            return URI.create(baseUrl + "/v2/transactions" + next);
        }
        if (next.startsWith("/")) {
            return URI.create(baseUrl + next);
        }
        if (next.startsWith(baseUrl + "/")) {
            return URI.create(next);
        }
        if (next.startsWith("http")) {
            // Nunca seguir URL de outro host: seria um SSRF entregue pela resposta.
            throw new IllegalStateException("Cursor da Pluggy aponta para outro host");
        }
        return uri("/v2/transactions", Map.of("accountId", externalAccountId, "after", next));
    }

    // O itemId chega do navegador (callback do widget): nada de "/", "..", "{" no caminho.
    private static String itemPath(String itemId) {
        if (itemId == null || !ITEM_ID.matcher(itemId).matches()) {
            throw new IllegalArgumentException("Identificador de conexão inválido");
        }
        return "/items/" + itemId;
    }

    private URI uri(String path, Map<String, String> query) {
        // Valores como variáveis de template: encode() os codifica por completo (inclusive "+", que
        // o servidor leria como espaço e corromperia um cursor em base64).
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl).path(path);
        query.keySet().forEach(name -> builder.queryParam(name, "{" + name + "}"));
        return builder.encode().buildAndExpand(query).toUri();
    }

    private <T> T withKey(java.util.function.Function<String, T> call) {
        if (!isConfigured()) {
            throw new IllegalStateException("Integração bancária não configurada (PLUGGY_CLIENT_ID/SECRET)");
        }
        try {
            return call.apply(apiKey());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) {
                throw e;
            }
            synchronized (this) {
                apiKeyExpiresAt = Instant.EPOCH;
            }
            return call.apply(apiKey());
        }
    }

    private synchronized String apiKey() {
        if (apiKey == null || Instant.now().isAfter(apiKeyExpiresAt)) {
            AuthResponse auth = rest.post().uri(uri("/auth", Map.of()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("clientId", clientId, "clientSecret", clientSecret))
                    .retrieve()
                    .body(AuthResponse.class);
            apiKey = auth.apiKey();
            apiKeyExpiresAt = Instant.now().plus(KEY_LIFETIME);
        }
        return apiKey;
    }

    static ExternalItem.Health health(PluggyItem item) {
        // Set.of(...).contains(null) lança NPE, e executionStatus pode vir nulo.
        if (item.status() != null && RECONNECT_STATUSES.contains(item.status())
                || item.executionStatus() != null && RECONNECT_EXECUTIONS.contains(item.executionStatus())) {
            return ExternalItem.Health.NEEDS_RECONNECT;
        }
        if ("OUTDATED".equals(item.status())) {
            return ExternalItem.Health.ERROR;
        }
        return ExternalItem.Health.OK;
    }

    private static String errorMessage(PluggyItem item) {
        return switch (health(item)) {
            case NEEDS_RECONNECT -> "O banco pediu uma nova autorização — reconecte para voltar a sincronizar";
            case ERROR -> item.error() != null && item.error().message() != null
                    ? item.error().message()
                    : "A última sincronização do banco falhou; vamos tentar de novo";
            case OK -> null;
        };
    }
}
