package com.budgetguardian.repository;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.Category;

/**
 * Persistence contract for categories.
 *
 * <p>Categories are fixed (seeded by the storage layer) — read-only
 * repository.</p>
 */
public interface CategoryRepository {

    /** @return all categories keyed by id. */
    HashMap<Integer, Category> findAll() throws StorageException;
}
