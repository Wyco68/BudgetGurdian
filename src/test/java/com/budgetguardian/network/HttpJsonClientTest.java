package com.budgetguardian.network;

import com.budgetguardian.dto.SettingDto;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the HTTP wrapper against a local stub server. */
class HttpJsonClientTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private HttpJsonClient client(int retries, String apiKey) {
        return new HttpJsonClient(baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(2),
                retries, apiKey);
    }

    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    @Test
    void getParsesJsonIntoType() throws ApiException {
        respond("/settings", 200, "[{\"key\":\"daily_budget\",\"value\":\"18000\"}]");
        SettingDto[] settings = client(0, null).get("/settings", SettingDto[].class);
        assertEquals(1, settings.length);
        assertEquals("daily_budget", settings[0].key);
        assertEquals("18000", settings[0].value);
    }

    @Test
    void non2xxRaisesApiExceptionWithStatusAndBackendMessage() {
        respond("/missing", 404, "{\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"Record not found\"}}");
        ApiException e = assertThrows(ApiException.class,
                () -> client(0, null).get("/missing", SettingDto.class));
        assertEquals(404, e.status());
        assertTrue(e.getMessage().contains("Record not found"));
    }

    @Test
    void apiKeyHeaderIsSentWhenConfigured() throws ApiException {
        AtomicReference<String> seenKey = new AtomicReference<>();
        server.createContext("/echo", exchange -> {
            seenKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        client(0, "secret-123").get("/echo", SettingDto.class);
        assertEquals("secret-123", seenKey.get());
    }

    @Test
    void postSendsJsonBodyAndParsesResponse() throws ApiException {
        AtomicReference<String> seenBody = new AtomicReference<>();
        server.createContext("/settings-put", exchange -> {
            seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = "{\"key\":\"k\",\"value\":\"v\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        SettingDto body = new SettingDto();
        body.key = "k";
        body.value = "v";
        SettingDto echoed = client(0, null).post("/settings-put", body, SettingDto.class);
        assertTrue(seenBody.get().contains("\"value\":\"v\""));
        assertEquals("v", echoed.value);
    }

    @Test
    void unreachableBackendFailsWithTransportError() {
        server.stop(0);
        ApiException e = assertThrows(ApiException.class,
                () -> client(0, null).get("/anything", SettingDto.class));
        assertEquals(0, e.status());
    }

    @Test
    void idempotentGetIsRetriedUntilServerRecovers() throws ApiException {
        // First attempt hits a connection that resets; emulate via a handler
        // that fails the exchange once, then serves normally.
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/flaky", exchange -> {
            if (calls.incrementAndGet() == 1) {
                exchange.close();   // abort without a response -> IOException client-side
                return;
            }
            byte[] bytes = "{\"key\":\"k\",\"value\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        SettingDto dto = client(2, null).get("/flaky", SettingDto.class);
        assertEquals("ok", dto.value);
        assertEquals(2, calls.get());
    }
}
