package com.budgetguardian.service;

import com.budgetguardian.repository.SettingsRepository;

import com.budgetguardian.repository.StorageException;
import java.time.LocalTime;

/**
 * Typed access to application settings.
 *
 * <p><b>Purpose:</b> settings live as strings in a
 * {@code HashMap<String, String>} (mirroring the {@code setting} table);
 * this service exposes them with their real types and defaults, and persists
 * edits from the Settings screen. Setting changes are not undoable — they are
 * preferences, not data modifications.</p>
 *
 * <p><b>Time complexity:</b> all reads O(1); writes O(1) upsert.</p>
 */
public final class SettingsService {

    /** Daily budget key; value in satang. Default 180 THB. */
    public static final String DAILY_BUDGET = "daily_budget";
    /** Danger weekly limit key; value in satang. Default 200 THB. */
    public static final String DANGER_WEEKLY_LIMIT = "danger_weekly_limit";
    /** Daily-reminder time key; value {@code HH:mm}. Default 20:00. */
    public static final String REMINDER_TIME = "reminder_time";

    private final DataStore store;
    private final EventBus bus;
    private final SettingsRepository settings;

    public SettingsService(DataStore store, EventBus bus, SettingsRepository settings) {
        this.store = store;
        this.bus = bus;
        this.settings = settings;
    }

    /** @return daily budget in satang (default 18000 = 180 THB). */
    public long dailyBudgetSatang() {
        return parseLong(store.settings().getOrDefault(DAILY_BUDGET, "18000"));
    }

    /** @return danger weekly limit in satang (default 20000 = 200 THB). */
    public long dangerWeeklyLimitSatang() {
        return parseLong(store.settings().getOrDefault(DANGER_WEEKLY_LIMIT, "20000"));
    }

    /** @return daily reminder time (default 20:00). */
    public LocalTime reminderTime() {
        return LocalTime.parse(store.settings().getOrDefault(REMINDER_TIME, "20:00"));
    }

    /** Persists one setting and publishes {@code SETTINGS_CHANGED}. */
    public void set(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new BudgetException("Setting key and value must not be blank");
        }
        try {
            settings.put(key, value);
        } catch (StorageException e) {
            throw new BudgetException("Failed to save setting " + key, e);
        }
        store.settings().put(key, value);
        bus.publish(EventType.SETTINGS_CHANGED);
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BudgetException("Corrupt numeric setting: " + value, e);
        }
    }
}
