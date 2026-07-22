package com.budgetguardian.app;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Spawns the Node.js backend on app launch when it is not already reachable,
 * and stops it again on app exit — only if this instance started it.
 *
 * <p>Only ever acts when {@link AppConfig#apiBaseUrl()} points at a loopback
 * host; a remote/hosted backend is never touched. The health check runs
 * first specifically so a second app instance, or a backend already started
 * by hand, is left alone rather than getting a duplicate process.</p>
 *
 * <p>The backend is launched as {@code node src/server.js} directly rather
 * than {@code npm start}: on Windows, npm wraps the script in a {@code cmd.exe}
 * layer, and destroying that top process leaves the real {@code node} child
 * running as an orphan. Invoking node directly means one process, one handle,
 * one clean {@link Process#destroy()} on shutdown — no leaked children across
 * repeated launches.</p>
 */
final class BackendLauncher {

    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(1);
    private static final int STARTUP_POLL_ATTEMPTS = 20;
    private static final Duration STARTUP_POLL_INTERVAL = Duration.ofMillis(500);

    private Process process;

    /**
     * Ensures the backend is reachable, starting it if configured to and
     * currently down. Safe to call even when the backend is remote or
     * already running: it only ever spawns a process it will also stop.
     */
    void ensureRunning(AppConfig config) {
        if (config.mode() != AppConfig.StorageMode.API) {
            return;
        }
        URI healthUri = healthUri(config.apiBaseUrl());
        if (healthUri == null || !isLoopback(healthUri) || !config.autoStartBackend()) {
            return;
        }
        if (isHealthy(healthUri)) {
            return;
        }
        Path backendDir = resolveBackendDir(config);
        if (backendDir == null) {
            return;
        }
        spawn(backendDir);
        waitUntilHealthy(healthUri);
    }

    /** Destroys the backend process, but only if this instance started it. */
    void stop() {
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    private void spawn(Path backendDir) {
        try {
            Path logFile = backendDir.resolve("logs").resolve("backend.log");
            Files.createDirectories(logFile.getParent());
            ProcessBuilder builder = new ProcessBuilder("node", "src/server.js")
                    .directory(backendDir.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                    .redirectErrorStream(true);
            process = builder.start();
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "backend-shutdown"));
        } catch (IOException e) {
            System.err.println("Could not start backend at " + backendDir + ": " + e.getMessage());
        }
    }

    private static Path resolveBackendDir(AppConfig config) {
        if (config.backendDir() != null) {
            Path configured = Path.of(config.backendDir());
            return Files.isDirectory(configured) ? configured : null;
        }
        Path sibling = Path.of("backend");
        return Files.isDirectory(sibling) ? sibling : null;
    }

    private static boolean isHealthy(URI healthUri) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(HEALTH_CHECK_TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder(healthUri)
                    .timeout(HEALTH_CHECK_TIMEOUT).GET().build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private void waitUntilHealthy(URI healthUri) {
        for (int attempt = 0; attempt < STARTUP_POLL_ATTEMPTS; attempt++) {
            if (isHealthy(healthUri)) {
                return;
            }
            try {
                Thread.sleep(STARTUP_POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static URI healthUri(String apiBaseUrl) {
        try {
            URI base = URI.create(apiBaseUrl);
            return new URI(base.getScheme(), base.getAuthority(), "/health", null, null);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isLoopback(URI uri) {
        String host = uri.getHost();
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
    }
}
