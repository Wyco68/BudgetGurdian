package com.budgetguardian.repository;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import org.junit.jupiter.api.Test;

import com.budgetguardian.repository.StorageException;
import com.budgetguardian.repository.sqlite.SqliteTransactionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trip tests for {@link TransactionRepository}. */
class TransactionRepositoryTest extends RepositoryTestBase {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 6);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 12, 0);

    private Transaction expense(LocalDate date, long satang) {
        return new Transaction(0, TransactionType.EXPENSE, "SCB", 1, "bread", satang, "lunch", date, NOW);
    }

    @Test
    void insertAssignsIdAndRoundTrips() throws Exception {
        TransactionRepository repository = new SqliteTransactionRepository(connection);
        Transaction saved = repository.insert(expense(DAY, 4_500));
        assertTrue(saved.id() > 0);
        DoublyLinkedList<Transaction> ledger = repository.findAll();
        assertEquals(1, ledger.size());
        assertEquals(saved, ledger.getFirst());     // record equality = full column fidelity
    }

    @Test
    void incomeWithNullCategoryRoundTrips() throws Exception {
        TransactionRepository repository = new SqliteTransactionRepository(connection);
        Transaction income = new Transaction(
                0, TransactionType.INCOME, "SAVING", null, null, 900_000, "scholarship", DAY, NOW);
        Transaction saved = repository.insert(income);
        Transaction loaded = repository.findAll().getFirst();
        assertEquals(saved, loaded);
        assertNull(loaded.categoryId());
        assertNull(loaded.itemName());
    }

    @Test
    void findAllIsChronologicalByDateThenId() throws Exception {
        TransactionRepository repository = new SqliteTransactionRepository(connection);
        Transaction later = repository.insert(expense(DAY.plusDays(1), 100));   // inserted first
        Transaction earlier = repository.insert(expense(DAY, 200));             // inserted second
        Transaction sameDay = repository.insert(expense(DAY, 300));
        DoublyLinkedList<Transaction> ledger = repository.findAll();
        assertEquals(earlier, ledger.get(0));    // date order beats insert order
        assertEquals(sameDay, ledger.get(1));    // same date → id order
        assertEquals(later, ledger.get(2));
    }

    @Test
    void updateRewritesColumns() throws Exception {
        TransactionRepository repository = new SqliteTransactionRepository(connection);
        Transaction saved = repository.insert(expense(DAY, 4_500));
        Transaction edited = new Transaction(saved.id(), TransactionType.EXPENSE, "SAVING", 2,
                null, 9_900, "taxi", DAY.plusDays(1), NOW);
        repository.update(edited);
        assertEquals(edited, repository.findAll().getFirst());
    }

    @Test
    void deleteRemovesRow() throws Exception {
        TransactionRepository repository = new SqliteTransactionRepository(connection);
        Transaction saved = repository.insert(expense(DAY, 4_500));
        repository.delete(saved.id());
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void updateOrDeleteMissingRowThrows() throws Exception {
        TransactionRepository repository = new SqliteTransactionRepository(connection);
        assertThrows(StorageException.class, () -> repository.delete(999));
        Transaction ghost = expense(DAY, 100).withId(999);
        assertThrows(StorageException.class, () -> repository.update(ghost));
    }

    @Test
    void foreignKeyRejectsUnknownAccount() {
        TransactionRepository repository = new SqliteTransactionRepository(connection);
        Transaction bad = new Transaction(
                0, TransactionType.EXPENSE, "KBANK", 1, null, 100, "x", DAY, NOW);
        assertThrows(StorageException.class, () -> repository.insert(bad));
    }
}
