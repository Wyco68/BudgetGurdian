package com.budgetguardian.repository;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.RefillItem;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trip tests for {@link RefillRepository}. */
class RefillRepositoryTest extends RepositoryTestBase {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 6);

    @Test
    void upsertInsertsThenUpdates() throws SQLException {
        RefillRepository repository = new RefillRepository(connection);
        RefillItem bread = new RefillItem("bread", 7, DAY, 2);
        repository.upsert(bread);
        HashMap<String, RefillItem> items = repository.findAll();
        assertEquals(1, items.size());
        assertEquals(bread, items.get("bread"));

        RefillItem updated = bread.recordPurchase(DAY.plusDays(5));
        repository.upsert(updated);                       // same key → update path
        items = repository.findAll();
        assertEquals(1, items.size());
        assertEquals(updated, items.get("bread"));
    }

    @Test
    void deleteRemovesRow() throws SQLException {
        RefillRepository repository = new RefillRepository(connection);
        repository.upsert(new RefillItem("milk", 3, DAY, 2));
        repository.delete("milk");
        assertTrue(repository.findAll().isEmpty());
        assertThrows(SQLException.class, () -> repository.delete("milk"));
    }
}
