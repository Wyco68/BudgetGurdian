package com.budgetguardian.repository;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.Category;
import org.junit.jupiter.api.Test;

import com.budgetguardian.repository.sqlite.SqliteCategoryRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the seeded category set and danger flags. */
class CategoryRepositoryTest extends RepositoryTestBase {

    @Test
    void findAllReturnsElevenSeededCategories() throws Exception {
        HashMap<Integer, Category> categories = new SqliteCategoryRepository(connection).findAll();
        assertEquals(11, categories.size());
        assertEquals("Food", categories.get(1).name());
        assertEquals("Gambling", categories.get(11).name());
    }

    @Test
    void onlyAlcoholAndGamblingAreDanger() throws Exception {
        HashMap<Integer, Category> categories = new SqliteCategoryRepository(connection).findAll();
        assertTrue(categories.get(10).danger());    // Alcohol
        assertTrue(categories.get(11).danger());    // Gambling
        for (int id = 1; id <= 9; id++) {
            assertFalse(categories.get(id).danger(), "unexpected danger flag on id " + id);
        }
    }
}
