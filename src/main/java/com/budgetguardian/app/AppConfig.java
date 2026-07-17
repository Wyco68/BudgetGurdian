package com.budgetguardian.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

/**
 * Desktop configuration: storage mode and REST-backend settings.
 *
 * <p><b>Source:</b> {@code config.properties} in the per-user data directory.
 * On first launch the bundled classpath template (with documentation
 * comments) is copied there, so users always have an editable, annotated
 * file. Unknown or missing keys fall back to safe defaults — the app never
 * fails to start because of configuration.</p>
 *
 * <p><b>Modes:</b> {@code local} (SQLite file, offline, default) or
 * {@code api} (REST backend; the desktop holds no database credentials —
 * only a base URL and an optional API key).</p>
 */
public final class AppConfig {

    /** Storage backends the desktop can run on. */
    public enum StorageMode { LOCAL, API }

    private static final String CONFIG_RESOURCE = "/config.properties";
    private static final String CONFIG_FILE = "config.properties";

    private final StorageMode mode;
    private final String apiBaseUrl;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final int retries;
    private final String apiKey;

    private AppConfig(Properties props) {
        this.mode = "api".equalsIgnoreCase(props.getProperty("storage.mode", "local"))
                ? StorageMode.API : StorageMode.LOCAL;
        this.apiBaseUrl = props.getProperty("api.baseUrl", "http://localhost:8080/api/v1").trim();
        this.connectTimeout = Duration.ofMillis(longProp(props, "api.connectTimeoutMs", 3000));
        this.requestTimeout = Duration.ofMillis(longProp(props, "api.requestTimeoutMs", 10000));
        this.retries = (int) longProp(props, "api.retries", 2);
        this.apiKey = props.getProperty("api.key", "").trim();
    }

    /**
     * Loads configuration from the user data directory, seeding the file from
     * the bundled template on first launch.
     */
    public static AppConfig load() {
        Path file = userDataDir().resolve(CONFIG_FILE);
        seedIfMissing(file);
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("Could not read " + file + "; using defaults: " + e.getMessage());
            }
        }
        return new AppConfig(props);
    }

    /** Loads configuration from explicit properties (tests). */
    public static AppConfig of(Properties props) {
        return new AppConfig(props);
    }

    /**
     * The OS-specific per-user data directory (created if absent). Holds the
     * SQLite database and the configuration file.
     *
     * <ul>
     *   <li>Windows: {@code %LOCALAPPDATA%\BudgetGuardian}</li>
     *   <li>macOS: {@code ~/Library/Application Support/BudgetGuardian}</li>
     *   <li>Linux/other: {@code ~/.budgetguardian}</li>
     * </ul>
     */
    public static Path userDataDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", ".");
        Path dir;
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path base = (localAppData != null && !localAppData.isBlank())
                    ? Path.of(localAppData)
                    : Path.of(home, "AppData", "Local");
            dir = base.resolve("BudgetGuardian");
        } else if (os.contains("mac")) {
            dir = Path.of(home, "Library", "Application Support", "BudgetGuardian");
        } else {
            dir = Path.of(home, ".budgetguardian");
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create data directory: " + dir, e);
        }
        return dir;
    }

    public StorageMode mode() {
        return mode;
    }

    public String apiBaseUrl() {
        return apiBaseUrl;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public int retries() {
        return retries;
    }

    /** @return the configured API key, or null when none is set */
    public String apiKey() {
        return apiKey.isEmpty() ? null : apiKey;
    }

    private static void seedIfMissing(Path file) {
        if (Files.exists(file)) {
            return;
        }
        try (InputStream template = AppConfig.class.getResourceAsStream(CONFIG_RESOURCE)) {
            if (template != null) {
                Files.copy(template, file);
            }
        } catch (IOException e) {
            // Non-fatal: defaults apply; the user can create the file manually.
            System.err.println("Could not seed " + file + ": " + e.getMessage());
        }
    }

    private static long longProp(Properties props, String key, long fallback) {
        try {
            return Long.parseLong(props.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            System.err.println("Invalid value for " + key + "; using " + fallback);
            return fallback;
        }
    }
}
