package com.budgetguardian.service;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Category;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.Transaction;

import java.time.LocalDate;

/**
 * Fast search across transactions and debts (Ctrl+F).
 *
 * <p><b>Algorithm choices, documented per requirement:</b></p>
 * <ul>
 *   <li>Free-text search — <em>linear search</em> over the ledger
 *       ({@code DoublyLinkedList}), newest first. Substring matching cannot
 *       be indexed by a hash map; O(n) is optimal without a suffix index and
 *       instant at personal-finance scale.</li>
 *   <li>Account / category / debt-id lookups — the {@code HashMap}s in
 *       {@link DataStore} give average O(1) hits; filtering the ledger by
 *       those keys is O(n) with O(1) key comparison per element.</li>
 *   <li>Date-range — O(n) filter over the chronological ledger.</li>
 * </ul>
 *
 * <p>Results are returned newest-first in a {@code DynamicArray} (O(1)
 * random access for table rendering).</p>
 */
public final class SearchService {

    private final DataStore store;

    public SearchService(DataStore store) {
        this.store = store;
    }

    /**
     * Free-text search over reason, item name, account id/name and category
     * name, case-insensitive. Linear search, O(n), newest first.
     */
    public DynamicArray<Transaction> searchTransactions(String query) {
        DynamicArray<Transaction> results = new DynamicArray<>();
        if (query == null || query.isBlank()) {
            return results;
        }
        String needle = query.trim().toLowerCase();
        Iterator<Transaction> it = store.ledger().descendingIterator();
        while (it.hasNext()) {
            Transaction txn = it.next();
            if (matches(txn, needle)) {
                results.append(txn);
            }
        }
        return results;
    }

    /** @return transactions of one account, newest first. O(n). */
    public DynamicArray<Transaction> byAccount(String accountId) {
        DynamicArray<Transaction> results = new DynamicArray<>();
        Iterator<Transaction> it = store.ledger().descendingIterator();
        while (it.hasNext()) {
            Transaction txn = it.next();
            if (txn.accountId().equals(accountId)) {
                results.append(txn);
            }
        }
        return results;
    }

    /** @return expenses of one category, newest first. O(n). */
    public DynamicArray<Transaction> byCategory(int categoryId) {
        DynamicArray<Transaction> results = new DynamicArray<>();
        Iterator<Transaction> it = store.ledger().descendingIterator();
        while (it.hasNext()) {
            Transaction txn = it.next();
            if (txn.categoryId() != null && txn.categoryId() == categoryId) {
                results.append(txn);
            }
        }
        return results;
    }

    /** @return transactions with {@code from <= date <= to}, newest first. O(n). */
    public DynamicArray<Transaction> byDateRange(LocalDate from, LocalDate to) {
        DynamicArray<Transaction> results = new DynamicArray<>();
        Iterator<Transaction> it = store.ledger().descendingIterator();
        while (it.hasNext()) {
            Transaction txn = it.next();
            if (!txn.date().isBefore(from) && !txn.date().isAfter(to)) {
                results.append(txn);
            }
        }
        return results;
    }

    /** @return debts whose counterparty name contains {@code query}. O(d). */
    public DynamicArray<Debt> searchDebts(String query) {
        DynamicArray<Debt> results = new DynamicArray<>();
        if (query == null || query.isBlank()) {
            return results;
        }
        String needle = query.trim().toLowerCase();
        Iterator<Debt> it = store.debts().values();
        while (it.hasNext()) {
            Debt debt = it.next();
            if (debt.person().toLowerCase().contains(needle)) {
                results.append(debt);
            }
        }
        return results;
    }

    private boolean matches(Transaction txn, String needle) {
        if (txn.reason().toLowerCase().contains(needle)) {
            return true;
        }
        if (txn.itemName() != null && txn.itemName().toLowerCase().contains(needle)) {
            return true;
        }
        if (txn.accountId().toLowerCase().contains(needle)) {
            return true;
        }
        if (txn.categoryId() != null) {
            Category category = store.categories().get(txn.categoryId());
            return category != null && category.name().toLowerCase().contains(needle);
        }
        return false;
    }
}
