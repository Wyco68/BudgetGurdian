package com.budgetguardian.repository;

import com.budgetguardian.datastructures.HashMap;

/**
 * Persistence contract for the settings key-value store
 * (daily budget, danger weekly limit, reminder time).
 */
public interface SettingsRepository {

    /** @return all settings keyed by name. */
    HashMap<String, String> findAll() throws StorageException;

    /** Inserts or replaces one setting. */
    void put(String key, String value) throws StorageException;
}
