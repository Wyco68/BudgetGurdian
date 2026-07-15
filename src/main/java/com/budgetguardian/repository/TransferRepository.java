package com.budgetguardian.repository;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.model.Transfer;

/**
 * Persistence contract for transfers between accounts.
 *
 * <p>Hydrates transfer history — chronological — into a
 * {@code DoublyLinkedList<Transfer>}; each row also becomes one edge of the
 * transfer {@code Graph} during startup rebuild.</p>
 */
public interface TransferRepository {

    /** @return full transfer history ordered by date then id. */
    DoublyLinkedList<Transfer> findAll() throws StorageException;

    /**
     * Inserts one transfer.
     *
     * @return the same transfer with its generated id
     */
    Transfer insert(Transfer transfer) throws StorageException;

    /** Deletes by id (undo of a transfer). */
    void delete(long id) throws StorageException;
}
