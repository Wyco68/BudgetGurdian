package com.budgetguardian.repository;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.model.Transaction;

/**
 * Persistence contract for the transaction ledger.
 *
 * <p>Hydrates the full ledger — chronologically ordered — into a
 * {@code DoublyLinkedList<Transaction>} at startup and persists every
 * mutation (insert on record, delete on undo, update on edit).</p>
 */
public interface TransactionRepository {

    /** @return full ledger ordered by date then id (stable chronology). */
    DoublyLinkedList<Transaction> findAll() throws StorageException;

    /**
     * Inserts one transaction.
     *
     * @return the same transaction with its generated id
     */
    Transaction insert(Transaction txn) throws StorageException;

    /**
     * Re-inserts a previously deleted transaction with its original id
     * (undo of a delete).
     */
    void restore(Transaction txn) throws StorageException;

    /** Rewrites all columns of an existing transaction (edit feature). */
    void update(Transaction txn) throws StorageException;

    /** Deletes by id (undo of an add). */
    void delete(long id) throws StorageException;
}
