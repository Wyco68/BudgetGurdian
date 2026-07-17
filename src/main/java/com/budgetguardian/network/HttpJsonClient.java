package com.budgetguardian.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Reusable JSON-over-HTTP wrapper around {@link java.net.http.HttpClient}.
 *
 * <p><b>Purpose:</b> the single place the desktop talks HTTP. Repositories
 * in {@code repository.api} call typed {@code get/post/put/delete} methods;
 * Gson handles all (de)serialization — JSON strings are never concatenated
 * by hand anywhere in the app.</p>
 *
 * <p><b>Behavior:</b></p>
 * <ul>
 *   <li>Per-request timeout and a connect timeout, both from configuration.</li>
 *   <li>Automatic retry with linear backoff for <em>idempotent</em> methods
 *       (GET and PUT — PUT bodies carry absolute state). POST and DELETE are
 *       never retried: a lost response would risk duplicate or ambiguous
 *       effects.</li>
 *   <li>Optional {@code X-API-Key} header when a key is configured.</li>
 *   <li>Non-2xx responses raise {@link ApiException} carrying the backend's
 *       {@code error.message} when present.</li>
 * </ul>
 *
 * <p>Thread-safe; one instance is shared by all API repositories.</p>
 */
public final class HttpJsonClient {

    private static final Gson GSON = new Gson();

    private final HttpClient client;
    private final String baseUrl;
    private final Duration requestTimeout;
    private final int maxRetries;
    private final String apiKey;

    /**
     * @param baseUrl        e.g. {@code http://localhost:8080/api/v1} (no trailing slash)
     * @param connectTimeout TCP connect budget
     * @param requestTimeout whole-request budget, applied per attempt
     * @param maxRetries     extra attempts for idempotent calls (0 = single try)
     * @param apiKey         value for the {@code X-API-Key} header, or null/blank to omit
     */
    public HttpJsonClient(String baseUrl, Duration connectTimeout, Duration requestTimeout,
                          int maxRetries, String apiKey) {
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.requestTimeout = requestTimeout;
        this.maxRetries = Math.max(0, maxRetries);
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey;
    }

    /** GET {@code path}, parsing the response into {@code type}. Retried. */
    public <T> T get(String path, Class<T> type) throws ApiException {
        return send(request(path).GET().build(), type, true);
    }

    /** POST {@code body} to {@code path}, parsing the response into {@code type}. Not retried. */
    public <T> T post(String path, Object body, Class<T> type) throws ApiException {
        return send(request(path).POST(jsonBody(body)).build(), type, false);
    }

    /** PUT {@code body} to {@code path}, parsing the response into {@code type}. Retried. */
    public <T> T put(String path, Object body, Class<T> type) throws ApiException {
        return send(request(path).PUT(jsonBody(body)).build(), type, true);
    }

    /** DELETE {@code path}, expecting no body back. Not retried. */
    public void delete(String path) throws ApiException {
        send(request(path).DELETE().build(), Void.class, false);
    }

    /** URL-encodes one path segment (item names may contain spaces etc.). */
    public static String encodeSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // ---- internals -------------------------------------------------------------

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header("Accept", "application/json");
        if (apiKey != null) {
            builder.header("X-API-Key", apiKey);
        }
        return builder;
    }

    private static HttpRequest.BodyPublisher jsonBody(Object body) {
        return HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8);
    }

    private <T> T send(HttpRequest requestTemplate, Class<T> type, boolean idempotent)
            throws ApiException {
        HttpRequest request = withContentType(requestTemplate);
        int attempts = idempotent ? maxRetries + 1 : 1;
        ApiException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                return parse(response, type);
            } catch (IOException e) {
                last = new ApiException("Cannot reach backend: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException("Request interrupted", e);
            }
            if (attempt < attempts) {
                sleepBackoff(attempt);
            }
        }
        throw last;
    }

    private static HttpRequest withContentType(HttpRequest original) {
        // GET/DELETE carry no body; harmless to always declare JSON.
        return HttpRequest.newBuilder(original, (name, value) -> true)
                .setHeader("Content-Type", "application/json")
                .build();
    }

    private static <T> T parse(HttpResponse<String> response, Class<T> type) throws ApiException {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new ApiException(status, errorMessage(response));
        }
        if (type == Void.class || response.body() == null || response.body().isEmpty()) {
            return null;
        }
        try {
            return GSON.fromJson(response.body(), type);
        } catch (JsonSyntaxException e) {
            throw new ApiException("Malformed response from backend", e);
        }
    }

    /** Extracts {@code error.message} from the backend's error shape, if present. */
    private static String errorMessage(HttpResponse<String> response) {
        String fallback = "Backend returned HTTP " + response.statusCode();
        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject error = root.getAsJsonObject("error");
            if (error != null && error.has("message")) {
                return fallback + ": " + error.get("message").getAsString();
            }
        } catch (RuntimeException ignored) {
            // Body was not the expected error shape; use the fallback.
        }
        return fallback;
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(200L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
