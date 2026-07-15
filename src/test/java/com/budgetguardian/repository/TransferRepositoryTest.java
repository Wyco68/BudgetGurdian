package com.budgetguardian.repository;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.model.Transfer;
import org.junit.jupiter.api.Test;

import com.budgetguardian.repository.StorageException;
import com.budgetguardian.repository.sqlite.SqliteTransferRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trip tests for {@link TransferRepository}. */
class TransferRepositoryTest extends RepositoryTestBase {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 6);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 12, 0);

    @Test
    void insertAssignsIdAndRoundTrips() throws Exception {
        TransferRepository repository = new SqliteTransferRepository(connection);
        Transfer saved = repository.insert(
                new Transfer(0, "SCB", "SAVING", 50_000, "ATM withdrawal", DAY, NOW));
        assertTrue(saved.id() > 0);
        DoublyLinkedList<Transfer> transfers = repository.findAll();
        assertEquals(1, transfers.size());
        assertEquals(saved, transfers.getFirst());
    }

    @Test
    void findAllIsChronological() throws Exception {
        TransferRepository repository = new SqliteTransferRepository(connection);
        Transfer later = repository.insert(
                new Transfer(0, "SCB", "SAVING", 100, "b", DAY.plusDays(2), NOW));
        Transfer earlier = repository.insert(
                new Transfer(0, "SAVING", "TRUEMONEY", 200, "a", DAY, NOW));
        DoublyLinkedList<Transfer> transfers = repository.findAll();
        assertEquals(earlier, transfers.getFirst());
        assertEquals(later, transfers.getLast());
    }

    @Test
    void deleteRemovesRow() throws Exception {
        TransferRepository repository = new SqliteTransferRepository(connection);
        Transfer saved = repository.insert(
                new Transfer(0, "SCB", "SAVING", 50_000, "atm", DAY, NOW));
        repository.delete(saved.id());
        assertTrue(repository.findAll().isEmpty());
        assertThrows(StorageException.class, () -> repository.delete(saved.id()));
    }

    @Test
    void schemaRejectsSelfTransferAndUnknownAccount() {
        TransferRepository repository = new SqliteTransferRepository(connection);
        // Self-transfer blocked by the record guard before SQL is reached.
        assertThrows(IllegalArgumentException.class,
                () -> new Transfer(0, "SCB", "SCB", 100, "loop", DAY, NOW));
        // Unknown account blocked by the foreign key.
        assertThrows(StorageException.class, () -> repository.insert(
                new Transfer(0, "SCB", "KBANK", 100, "ghost", DAY, NOW)));
    }
}
