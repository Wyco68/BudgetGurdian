package com.budgetguardian.repository;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtPayment;

/**
 * Persistence contract for debts and their partial payments.
 *
 * <p>Hydrates debts into a {@code HashMap<Long, Debt>} (O(1) lookup) and
 * each debt's partial-payment history into a chronological
 * {@code DoublyLinkedList<DebtPayment>}. Settlement status is derived by the
 * service layer (payments sum ≥ amount) and persisted here.</p>
 */
public interface DebtRepository {

    /** @return all debts keyed by id. */
    HashMap<Long, Debt> findAll() throws StorageException;

    /** @return every payment of every debt, chronological (payment date then id). */
    DoublyLinkedList<DebtPayment> findAllPayments() throws StorageException;

    /**
     * Inserts one debt.
     *
     * @return the same debt with its generated id
     */
    Debt insert(Debt debt) throws StorageException;

    /** Persists a status flip (OPEN ↔ SETTLED) with its settled date. */
    void updateStatus(Debt debt) throws StorageException;

    /** Deletes a debt; its payments cascade at the storage level. */
    void delete(long debtId) throws StorageException;

    /**
     * Inserts one partial payment.
     *
     * @return the same payment with its generated id
     */
    DebtPayment insertPayment(DebtPayment payment) throws StorageException;

    /** Deletes one payment (undo of a partial payment). */
    void deletePayment(long paymentId) throws StorageException;
}
