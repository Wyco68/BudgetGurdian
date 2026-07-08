package com.budgetguardian.repository;

import com.budgetguardian.datastructures.HashMap;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Round-trip tests for {@link SettingsRepository} against seeded settings. */
class SettingsRepositoryTest extends RepositoryTestBase {

    @Test
    void findAllReturnsSeededDefaults() throws SQLException {
        HashMap<String, String> settings = new SettingsRepository(connection).findAll();
        assertEquals(3, settings.size());
        assertEquals("18000", settings.get("daily_budget"));
        assertEquals("20000", settings.get("danger_weekly_limit"));
        assertEquals("20:00", settings.get("reminder_time"));
    }

    @Test
    void putUpsertsExistingAndNewKeys() throws SQLException {
        SettingsRepository repository = new SettingsRepository(connection);
        repository.put("daily_budget", "25000");           // update
        repository.put("theme", "dark");                   // insert
        HashMap<String, String> settings = repository.findAll();
        assertEquals("25000", settings.get("daily_budget"));
        assertEquals("dark", settings.get("theme"));
        assertEquals(4, settings.size());
    }
}
