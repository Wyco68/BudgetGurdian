package com.budgetguardian.app;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AppConfigTest {

    @Test
    void defaultsToLocalModeWithSaneApiSettings() {
        AppConfig config = AppConfig.of(new Properties());
        assertEquals(AppConfig.StorageMode.LOCAL, config.mode());
        assertEquals("http://localhost:8080/api/v1", config.apiBaseUrl());
        assertEquals(Duration.ofSeconds(3), config.connectTimeout());
        assertEquals(Duration.ofSeconds(10), config.requestTimeout());
        assertEquals(2, config.retries());
        assertNull(config.apiKey());
    }

    @Test
    void readsApiModeSettings() {
        Properties props = new Properties();
        props.setProperty("storage.mode", "api");
        props.setProperty("api.baseUrl", "https://budget.example.com/api/v1");
        props.setProperty("api.connectTimeoutMs", "5000");
        props.setProperty("api.requestTimeoutMs", "15000");
        props.setProperty("api.retries", "4");
        props.setProperty("api.key", "s3cret");
        AppConfig config = AppConfig.of(props);
        assertEquals(AppConfig.StorageMode.API, config.mode());
        assertEquals("https://budget.example.com/api/v1", config.apiBaseUrl());
        assertEquals(Duration.ofSeconds(5), config.connectTimeout());
        assertEquals(Duration.ofSeconds(15), config.requestTimeout());
        assertEquals(4, config.retries());
        assertEquals("s3cret", config.apiKey());
    }

    @Test
    void invalidNumbersFallBackToDefaults() {
        Properties props = new Properties();
        props.setProperty("api.retries", "many");
        props.setProperty("storage.mode", "cloud");   // unknown -> local
        AppConfig config = AppConfig.of(props);
        assertEquals(2, config.retries());
        assertEquals(AppConfig.StorageMode.LOCAL, config.mode());
    }
}
