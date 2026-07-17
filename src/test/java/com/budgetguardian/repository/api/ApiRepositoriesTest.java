package com.budgetguardian.repository.api;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.Account;
import com.budgetguardian.model.Category;
import com.budgetguardian.model.RefillItem;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.network.HttpJsonClient;
import com.budgetguardian.repository.Repositories;
import com.budgetguardian.repository.StorageException;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the REST repository implementations against a stub HTTP server and
 * verifies they build the same custom data structures the SQLite versions do.
 */
class ApiRepositoriesTest {

    private HttpServer server;
    private Repositories repos;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        repos = ApiRepositories.create(new HttpJsonClient(
                baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(2), 0, null));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void respond(String path, String method, int status, String body) {
        server.createContext(path, exchange -> {
            if (!exchange.getRequestMethod().equals(method)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            write(exchange, status, body);
        });
    }

    private static void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    @Test
    void accountsHydrateIntoCustomHashMap() throws StorageException {
        respond("/accounts", "GET", 200, """
                [{"id":"SCB","name":"SCB","balanceSatang":120050,"displayOrder":3},
                 {"id":"SAVING","name":"Saving","balanceSatang":0,"displayOrder":1}]""");
        HashMap<String, Account> accounts = repos.accounts().findAll();
        assertEquals(2, accounts.size());
        assertEquals(120050, accounts.get("SCB").balanceSatang());
    }

    @Test
    void categoriesHydrateIntoCustomHashMap() throws StorageException {
        respond("/categories", "GET", 200,
                "[{\"id\":10,\"name\":\"Alcohol\",\"danger\":true}]");
        HashMap<Integer, Category> categories = repos.categories().findAll();
        assertTrue(categories.get(10).danger());
    }

    @Test
    void ledgerHydratesIntoCustomLinkedListInServerOrder() throws StorageException {
        respond("/transactions", "GET", 200, """
                [{"id":1,"type":"EXPENSE","accountId":"SCB","categoryId":1,"itemName":"Rice",
                  "amountSatang":5000,"reason":"lunch","date":"2026-07-15","createdAt":"2026-07-15T12:00:00"},
                 {"id":2,"type":"INCOME","accountId":"SAVING","categoryId":null,"itemName":null,
                  "amountSatang":100000,"reason":"salary","date":"2026-07-16","createdAt":"2026-07-16T09:00:00"}]""");
        DoublyLinkedList<Transaction> ledger = repos.transactions().findAll();
        assertEquals(2, ledger.size());
        assertEquals(1, ledger.getFirst().id());
        assertEquals(2, ledger.getLast().id());
        assertEquals(TransactionType.INCOME, ledger.getLast().type());
    }

    @Test
    void insertReturnsTransactionWithGeneratedId() throws StorageException {
        respond("/transactions", "POST", 201, """
                {"id":77,"type":"EXPENSE","accountId":"SCB","categoryId":1,"itemName":"Rice",
                 "amountSatang":5000,"reason":"lunch","date":"2026-07-16","createdAt":"2026-07-16T12:00:00"}""");
        Transaction unsaved = new Transaction(0, TransactionType.EXPENSE, "SCB", 1,
                "Rice", 5000, "lunch", LocalDate.of(2026, 7, 16),
                LocalDateTime.of(2026, 7, 16, 12, 0));
        Transaction saved = repos.transactions().insert(unsaved);
        assertEquals(77, saved.id());
        assertEquals(unsaved.reason(), saved.reason());
    }

    @Test
    void backendErrorSurfacesAsStorageException() {
        respond("/transactions/9", "DELETE", 404,
                "{\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"Record not found\"}}");
        StorageException e = assertThrows(StorageException.class,
                () -> repos.transactions().delete(9));
        assertTrue(e.getMessage().contains("delete transaction 9"));
    }

    @Test
    void refillUpsertEncodesItemNameInPath() throws StorageException {
        AtomicReference<String> seenPath = new AtomicReference<>();
        server.createContext("/refill-items/", exchange -> {
            seenPath.set(exchange.getRequestURI().getRawPath());
            write(exchange, 200,
                    "{\"name\":\"dish soap\",\"intervalDays\":14.0,\"lastPurchase\":\"2026-07-16\",\"purchaseCount\":2}");
        });
        repos.refills().upsert(new RefillItem("dish soap", 14.0, LocalDate.of(2026, 7, 16), 2));
        assertEquals("/refill-items/dish%20soap", seenPath.get());
    }

    @Test
    void settingsPutRoundTrips() throws StorageException {
        AtomicReference<String> seenBody = new AtomicReference<>();
        server.createContext("/settings/daily_budget", exchange -> {
            seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            write(exchange, 200, "{\"key\":\"daily_budget\",\"value\":\"20000\"}");
        });
        repos.settings().put("daily_budget", "20000");
        assertTrue(seenBody.get().contains("\"value\":\"20000\""));
    }
}
