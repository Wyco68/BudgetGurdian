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
    void findAllReturnsSixSeededCategories() throws Exception {
        HashMap<Integer, Category> categories = new SqliteCategoryRepository(connection).findAll();
        assertEquals(6, categories.size());
        assertEquals("DailySpending", categories.get(1).name());
        assertEquals("Refill", categories.get(2).name());
        assertEquals("Extra", categories.get(3).name());
        assertEquals("Bill", categories.get(4).name());
        assertEquals("Alcohol", categories.get(10).name());
        assertEquals("Gamble", categories.get(11).name());
    }

    @Test
    void onlyAlcoholAndGambleAreDanger() throws Exception {
        HashMap<Integer, Category> categories = new SqliteCategoryRepository(connection).findAll();
        assertTrue(categories.get(10).danger());    // Alcohol
        assertTrue(categories.get(11).danger());    // Gamble
        for (int id : new int[] {1, 2, 3, 4}) {
            assertFalse(categories.get(id).danger(), "unexpected danger flag on id " + id);
        }
    }
}
