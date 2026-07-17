package com.budgetguardian.integration;

import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.network.HttpJsonClient;
import com.budgetguardian.repository.Repositories;
import com.budgetguardian.repository.api.ApiRepositories;
import com.budgetguardian.service.BudgetException;
import com.budgetguardian.service.ServiceContext;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end synchronization test: the real service layer (validation,
 * in-memory structures, undo, events) running on the REST repositories
 * against a stateful stub backend.
 *
 * <p>Verifies the full write path — mutate remotely first, memory second —
 * plus the failure contract (remote save fails ⇒ memory untouched) and
 * restart consistency (a second hydration sees exactly what was persisted).</p>
 */
class ApiSyncIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 6);   // a Monday
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 12, 0);

    private StubBackend backend;
    private ServiceContext services;

    @BeforeEach
    void start() throws IOException {
        backend = new StubBackend();
        backend.start();
        services = new ServiceContext(repositories(), () -> DAY);
    }

    @AfterEach
    void stop() {
        backend.stopServer();
    }

    private Repositories repositories() {
        return ApiRepositories.create(new HttpJsonClient(
                backend.baseUrl(), Duration.ofSeconds(2), Duration.ofSeconds(2), 0, null));
    }

    private static Transaction expense(long amountSatang) {
        return new Transaction(0, TransactionType.EXPENSE, "SCB", 1, "Rice",
                amountSatang, "lunch", DAY, NOW);
    }

    @Test
    void startupHydratesLedgerAndBalancesFromBackend() {
        backend.seedTransaction(expense(7000).withId(1));
        backend.balances.put("SCB", -7000L);
        ServiceContext fresh = new ServiceContext(repositories(), () -> DAY);
        assertEquals(1, fresh.store().ledger().size());
        assertEquals(-7000, fresh.store().accounts().get("SCB").balanceSatang());
        assertEquals(7000, fresh.store().dailyTotal(DAY));
    }

    @Test
    void addPersistsRemotelyThenUpdatesMemory() {
        Transaction saved = services.transactions().add(expense(5000));
        assertTrue(saved.id() > 0);
        // Remote state: row stored under the generated id, balance is absolute.
        assertEquals(1, backend.transactions.size());
        assertEquals(-5000L, backend.balances.get("SCB"));
        // Memory state: ledger, balance and derived totals all updated.
        assertEquals(1, services.store().ledger().size());
        assertEquals(-5000, services.store().accounts().get("SCB").balanceSatang());
        assertEquals(5000, services.store().dailyTotal(DAY));
    }

    @Test
    void undoReversesRemoteAndMemoryState() {
        services.transactions().add(expense(5000));
        services.undo().undo();
        assertEquals(0, backend.transactions.size());
        assertEquals(0L, backend.balances.get("SCB"));
        assertEquals(0, services.store().ledger().size());
        assertEquals(0, services.store().accounts().get("SCB").balanceSatang());
    }

    @Test
    void deleteThenUndoRestoresOriginalIdRemotely() {
        Transaction saved = services.transactions().add(expense(5000));
        services.transactions().delete(saved.id());
        assertEquals(0, backend.transactions.size());
        services.undo().undo();   // restore with original id
        assertEquals(1, backend.transactions.size());
        assertTrue(backend.transactions.containsKey(saved.id()));
        assertEquals(-5000L, backend.balances.get("SCB"));
    }

    @Test
    void backendFailureLeavesMemoryIntact() {
        services.transactions().add(expense(5000));
        backend.stopServer();   // backend goes down mid-session
        assertThrows(BudgetException.class, () -> services.transactions().add(expense(999)));
        // Local memory still reflects only the successful operation.
        assertEquals(1, services.store().ledger().size());
        assertEquals(-5000, services.store().accounts().get("SCB").balanceSatang());
    }

    @Test
    void secondStartupSeesExactlyWhatWasPersisted() {
        services.transactions().add(expense(5000));
        services.transactions().add(expense(2500));
        ServiceContext restarted = new ServiceContext(repositories(), () -> DAY);
        assertEquals(2, restarted.store().ledger().size());
        assertEquals(-7500, restarted.store().accounts().get("SCB").balanceSatang());
        assertEquals(7500, restarted.store().dailyTotal(DAY));
    }

    // ---- stateful stub backend -------------------------------------------------

    /**
     * Minimal in-memory imitation of the Node backend: same routes, same DTO
     * shapes, same id generation, enough for the desktop's full write path.
     */
    private static final class StubBackend {

        private static final Gson GSON = new Gson();
        private static final Pattern TXN_ID = Pattern.compile("^/transactions/(\\d+)$");
        private static final Pattern TXN_RESTORE = Pattern.compile("^/transactions/(\\d+)/restore$");
        private static final Pattern BALANCE = Pattern.compile("^/accounts/([^/]+)/balance$");

        final Map<Long, JsonObject> transactions = new LinkedHashMap<>();
        final Map<String, Long> balances = new LinkedHashMap<>();
        private HttpServer server;
        private long nextId = 1;

        void start() throws IOException {
            balances.put("SAVING", 0L);
            balances.put("SCB", 0L);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        void stopServer() {
            server.stop(0);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void seedTransaction(Transaction txn) {
            JsonObject dto = new JsonObject();
            dto.addProperty("id", txn.id());
            dto.addProperty("type", txn.type().name());
            dto.addProperty("accountId", txn.accountId());
            dto.addProperty("categoryId", txn.categoryId());
            dto.addProperty("itemName", txn.itemName());
            dto.addProperty("amountSatang", txn.amountSatang());
            dto.addProperty("reason", txn.reason());
            dto.addProperty("date", txn.date().toString());
            dto.addProperty("createdAt", txn.createdAt().toString());
            transactions.put(txn.id(), dto);
            nextId = Math.max(nextId, txn.id() + 1);
        }

        private synchronized void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response = route(method, path, body);
            if (response == null) {
                reply(exchange, 404, "{\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"no route\"}}");
            } else {
                reply(exchange, 200, response);
            }
        }

        private String route(String method, String path, String body) {
            if (method.equals("GET")) {
                return switch (path) {
                    case "/accounts" -> accountsJson();
                    case "/categories" ->
                            "[{\"id\":1,\"name\":\"Food\",\"danger\":false},{\"id\":10,\"name\":\"Alcohol\",\"danger\":true}]";
                    case "/transactions" -> GSON.toJson(new ArrayList<>(transactions.values()));
                    case "/transfers", "/debts", "/debt-payments", "/refill-items" -> "[]";
                    case "/settings" ->
                            "[{\"key\":\"daily_budget\",\"value\":\"18000\"},{\"key\":\"danger_weekly_limit\",\"value\":\"20000\"},{\"key\":\"reminder_time\",\"value\":\"20:00\"}]";
                    default -> null;
                };
            }
            if (method.equals("POST") && path.equals("/transactions")) {
                JsonObject dto = JsonParser.parseString(body).getAsJsonObject();
                long id = nextId++;
                dto.addProperty("id", id);
                transactions.put(id, dto);
                return GSON.toJson(dto);
            }
            Matcher restore = TXN_RESTORE.matcher(path);
            if (method.equals("POST") && restore.matches()) {
                long id = Long.parseLong(restore.group(1));
                JsonObject dto = JsonParser.parseString(body).getAsJsonObject();
                dto.addProperty("id", id);
                transactions.put(id, dto);
                nextId = Math.max(nextId, id + 1);
                return GSON.toJson(dto);
            }
            Matcher txn = TXN_ID.matcher(path);
            if (method.equals("DELETE") && txn.matches()) {
                return transactions.remove(Long.parseLong(txn.group(1))) != null ? "" : null;
            }
            if (method.equals("PUT") && txn.matches()) {
                long id = Long.parseLong(txn.group(1));
                if (!transactions.containsKey(id)) {
                    return null;
                }
                JsonObject dto = JsonParser.parseString(body).getAsJsonObject();
                dto.addProperty("id", id);
                transactions.put(id, dto);
                return GSON.toJson(dto);
            }
            Matcher balance = BALANCE.matcher(path);
            if (method.equals("PUT") && balance.matches()) {
                String accountId = balance.group(1);
                if (!balances.containsKey(accountId)) {
                    return null;
                }
                long value = JsonParser.parseString(body).getAsJsonObject()
                        .get("balanceSatang").getAsLong();
                balances.put(accountId, value);
                return accountJson(accountId);
            }
            return null;
        }

        private String accountsJson() {
            List<String> entries = new ArrayList<>();
            int order = 1;
            for (Map.Entry<String, Long> entry : balances.entrySet()) {
                entries.add(String.format(
                        "{\"id\":\"%s\",\"name\":\"%s\",\"balanceSatang\":%d,\"displayOrder\":%d}",
                        entry.getKey(), entry.getKey(), entry.getValue(), order++));
            }
            return "[" + String.join(",", entries) + "]";
        }

        private String accountJson(String accountId) {
            return String.format(
                    "{\"id\":\"%s\",\"name\":\"%s\",\"balanceSatang\":%d,\"displayOrder\":1}",
                    accountId, accountId, balances.get(accountId));
        }

        private static void reply(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
        }
    }
}
