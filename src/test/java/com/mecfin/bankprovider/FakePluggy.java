package com.mecfin.bankprovider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Pluggy falso para os testes de integração (HttpServer do JDK, sem dependência). Reproduz o que
 * o adapter usa da API real (docs.pluggy.ai): POST /auth → apiKey; X-API-KEY em tudo; /items,
 * /accounts?type=BANK e /v2/transactions com paginação por cursor ("next" em query string).
 */
final class FakePluggy {

    record Tx(String id, String date, String amount, String description, String status) {
    }

    private final HttpServer server;
    final Map<String, String> itemOwner = new ConcurrentHashMap<>();
    final Map<String, String> itemStatus = new ConcurrentHashMap<>();
    final Map<String, List<Map<String, String>>> accountsByItem = new ConcurrentHashMap<>();
    final Map<String, List<Tx>> transactionsByAccount = new ConcurrentHashMap<>();
    final List<String> connectTokenBodies = new CopyOnWriteArrayList<>();
    final List<String> deletedItems = new CopyOnWriteArrayList<>();
    volatile boolean down;

    FakePluggy() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        Map<String, String> query = query(ex.getRequestURI().getRawQuery());
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (path.equals("/auth") && method.equals("POST")) {
            send(ex, 200, "{\"apiKey\":\"key-123\"}");
            return;
        }
        if (!"key-123".equals(ex.getRequestHeaders().getFirst("X-API-KEY"))) {
            send(ex, 401, "{\"message\":\"unauthorized\"}");
            return;
        }
        if (down) {
            send(ex, 500, "{\"message\":\"internal error\"}");
            return;
        }
        if (path.equals("/connect_token") && method.equals("POST")) {
            connectTokenBodies.add(body);
            send(ex, 200, "{\"accessToken\":\"connect-token-abc\"}");
        } else if (path.startsWith("/items/") && method.equals("GET")) {
            String id = path.substring("/items/".length());
            if (!itemOwner.containsKey(id)) {
                send(ex, 404, "{\"message\":\"not found\"}");
                return;
            }
            send(ex, 200, """
                    {"id":"%s","status":"%s","executionStatus":"SUCCESS","clientUserId":"%s",
                     "connector":{"id":201,"name":"Pluggy Bank","imageUrl":"https://cdn.pluggy.ai/assets/connector-icons/201.svg","primaryColor":"ef294b"}}
                    """.formatted(id, itemStatus.getOrDefault(id, "UPDATED"), itemOwner.get(id)));
        } else if (path.startsWith("/items/") && method.equals("DELETE")) {
            deletedItems.add(path.substring("/items/".length()));
            send(ex, 200, "{}");
        } else if (path.equals("/accounts") && "BANK".equals(query.get("type"))) {
            String results = accountsByItem.getOrDefault(query.get("itemId"), List.of()).stream()
                    .map(a -> """
                            {"id":"%s","type":"BANK","subtype":"%s","name":"%s","marketingName":null,"number":"0001/%s","balance":%s,"currencyCode":"BRL","itemId":"%s"}
                            """.formatted(a.get("id"), a.get("subtype"), a.get("name"), a.get("id"), a.get("balance"),
                            query.get("itemId")))
                    .collect(Collectors.joining(","));
            send(ex, 200, "{\"page\":1,\"total\":1,\"totalPages\":1,\"results\":[" + results + "]}");
        } else if (path.equals("/v2/transactions")) {
            List<Tx> all = transactionsByAccount.getOrDefault(query.get("accountId"), List.of()).stream()
                    .filter(t -> query.get("dateFrom") == null || t.date().compareTo(query.get("dateFrom")) >= 0)
                    .toList();
            // Duas transações por página, para exercitar o cursor.
            int start = query.containsKey("after") ? Integer.parseInt(query.get("after")) : 0;
            List<Tx> page = all.subList(Math.min(start, all.size()), Math.min(start + 2, all.size()));
            String next = start + 2 < all.size()
                    ? "\"?accountId=" + query.get("accountId") + "&after=" + (start + 2) + "\""
                    : "null";
            String results = page.stream().map(t -> """
                    {"id":"%s","accountId":"%s","description":"%s","amount":%s,"date":"%sT00:00:00.000Z","status":"%s","type":"%s","currencyCode":"BRL"}
                    """.formatted(t.id(), query.get("accountId"), t.description(), t.amount(), t.date(), t.status(),
                    t.amount().startsWith("-") ? "DEBIT" : "CREDIT")).collect(Collectors.joining(","));
            send(ex, 200, "{\"results\":[" + results + "],\"next\":" + next + "}");
        } else {
            send(ex, 404, "{\"message\":\"no route\"}");
        }
    }

    void addAccount(String itemId, String accountId, String name, String balance) {
        accountsByItem.computeIfAbsent(itemId, k -> new CopyOnWriteArrayList<>())
                .add(new HashMap<>(Map.of("id", accountId, "subtype", "CHECKING_ACCOUNT", "name", name, "balance", balance)));
    }

    void addTransaction(String accountId, Tx tx) {
        transactionsByAccount.computeIfAbsent(accountId, k -> new CopyOnWriteArrayList<>()).add(tx);
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null) {
            return map;
        }
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return map;
    }

    private static void send(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
