package com.budgetguardian.repository;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.Bill;

/** Persistence contract for recurring bills. */
public interface BillRepository {

    /** @return all bills keyed by id. */
    HashMap<Long, Bill> findAll() throws StorageException;

    /**
     * Inserts one bill.
     *
     * @return the same bill with its generated id
     */
    Bill insert(Bill bill) throws StorageException;

    /** Persists a new {@code lastPaidDate} after a payment. */
    void updateLastPaid(Bill bill) throws StorageException;

    /** Deletes a bill. */
    void delete(long billId) throws StorageException;
}
