package com.budgetguardian.repository;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.RefillItem;

/**
 * Persistence contract for confirmed refillable items.
 *
 * <p>Only items the user answered "Yes" for exist here — a declined item
 * leaves no row, so the next duplicate purchase asks again.</p>
 */
public interface RefillRepository {

    /** @return all refillable items keyed by normalized name. */
    HashMap<String, RefillItem> findAll() throws StorageException;

    /** Inserts or replaces one item (name is the primary key). */
    void upsert(RefillItem item) throws StorageException;

    /** Deletes one item (undo of a "Yes" confirmation). */
    void delete(String name) throws StorageException;
}
