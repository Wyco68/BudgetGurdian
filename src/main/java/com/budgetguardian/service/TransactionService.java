package com.budgetguardian.service;

import com.budgetguardian.model.Account;
import com.budgetguardian.model.Category;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.repository.AccountRepository;
import com.budgetguardian.repository.TransactionRepository;
import com.budgetguardian.repository.TransactionRunner;

import com.budgetguardian.repository.StorageException;

/**
 * Business logic for expenses, income and withdrawals.
 *
 * <p><b>Write path (all mutations):</b> validate → SQLite inside one
 * transaction (row + balance) → in-memory structures ({@code DoublyLinkedList}
 * ledger, {@code HashMap} balances and totals, {@code CircularBuffer} recent)
 * → push inverse {@link Action} on the undo {@code Stack} → publish events.
 * Database first: a crash mid-operation leaves memory rebuildable from disk,
 * never ahead of it.</p>
 *
 * <p><b>Time complexity:</b> add O(1) in memory (plus SQLite B-tree insert);
 * delete/edit O(n) ledger scan by id + O(20) buffer rebuild.</p>
 */
public final class TransactionService {

    /** Name of the catch-all category that requires a reason on every expense. */
    public static final String EXTRA_CATEGORY_NAME = "Extra";

    /** Name of the category whose expenses drive refill-item tracking. */
    public static final String REFILL_CATEGORY_NAME = "Refill";

    private final DataStore store;
    private final EventBus bus;
    private final TransactionRunner runner;
    private final TransactionRepository transactions;
    private final AccountRepository accounts;

    public TransactionService(DataStore store, EventBus bus, TransactionRunner runner,
                              TransactionRepository transactions, AccountRepository accounts) {
        this.store = store;
        this.bus = bus;
        this.runner = runner;
        this.transactions = transactions;
        this.accounts = accounts;
    }

    /**
     * Records a new transaction (id 0), persists it atomically with the
     * balance change, updates all in-memory state and pushes an undo action.
     *
     * @return the saved transaction with its generated id
     * @throws BudgetException on unknown account/category or storage failure
     */
    public Transaction add(Transaction txn) {
        validate(txn);
        Transaction saved;
        try {
            saved = runner.run(() -> {
                Transaction inserted = transactions.insert(txn);
                Account preview = previewBalance(txn, +1);
                accounts.updateBalance(preview.id(), preview.balanceSatang());
                return inserted;
            });
        } catch (StorageException e) {
            throw new BudgetException("Failed to save transaction", e);
        }
        store.ledger().addLast(saved);
        store.applyToBalance(saved, +1);
        store.applyToTotals(saved, +1);
        store.recentTransactions().add(saved);
        store.undoStack().push(new Action.AddTransaction(saved));
        publishLedgerEvents();
        return saved;
    }

    /**
     * Deletes a transaction by id, reversing its balance and totals.
     *
     * @throws BudgetException if the id is not in the ledger
     */
    public void delete(long id) {
        Transaction txn = findById(id);
        try {
            runner.run(() -> {
                transactions.delete(id);
                Account preview = previewBalance(txn, -1);
                accounts.updateBalance(preview.id(), preview.balanceSatang());
                return null;
            });
        } catch (StorageException e) {
            throw new BudgetException("Failed to delete transaction", e);
        }
        removeFromMemory(txn);
        store.undoStack().push(new Action.DeleteTransaction(txn));
        publishLedgerEvents();
    }

    /**
     * Replaces a transaction's fields (same id), adjusting balances and
     * totals by the delta between old and new.
     *
     * @throws BudgetException if the id is not in the ledger
     */
    public Transaction edit(Transaction edited) {
        validate(edited);
        Transaction before = findById(edited.id());
        try {
            runner.run(() -> {
                transactions.update(edited);
                persistSwapBalances(before, edited);
                return null;
            });
        } catch (StorageException e) {
            throw new BudgetException("Failed to edit transaction", e);
        }
        swapInMemory(before, edited);
        store.undoStack().push(new Action.EditTransaction(before, edited));
        publishLedgerEvents();
        return edited;
    }

    // ---- package-private inverse operations used by UndoService ---------------

    /** Inverse of {@link #add}: delete without pushing a new action. */
    void undoAdd(Transaction txn) {
        try {
            runner.run(() -> {
                transactions.delete(txn.id());
                Account preview = previewBalance(txn, -1);
                accounts.updateBalance(preview.id(), preview.balanceSatang());
                return null;
            });
        } catch (StorageException e) {
            throw new BudgetException("Undo failed", e);
        }
        removeFromMemory(txn);
        publishLedgerEvents();
    }

    /** Inverse of {@link #delete}: restore with the original id. */
    void undoDelete(Transaction txn) {
        try {
            runner.run(() -> {
                transactions.restore(txn);
                Account preview = previewBalance(txn, +1);
                accounts.updateBalance(preview.id(), preview.balanceSatang());
                return null;
            });
        } catch (StorageException e) {
            throw new BudgetException("Undo failed", e);
        }
        store.ledger().addLast(txn);
        store.applyToBalance(txn, +1);
        store.applyToTotals(txn, +1);
        store.rebuildRecentTransactions();
        publishLedgerEvents();
    }

    /** Inverse of {@link #edit}: swap {@code after} back to {@code before}. */
    void undoEdit(Transaction before, Transaction after) {
        try {
            runner.run(() -> {
                transactions.update(before);
                persistSwapBalances(after, before);
                return null;
            });
        } catch (StorageException e) {
            throw new BudgetException("Undo failed", e);
        }
        swapInMemory(after, before);
        publishLedgerEvents();
    }

    // ---- internals -------------------------------------------------------------

    private void validate(Transaction txn) {
        if (!store.accounts().containsKey(txn.accountId())) {
            throw new BudgetException("Unknown account: " + txn.accountId());
        }
        if (txn.type() == TransactionType.EXPENSE) {
            Category category = store.categories().get(txn.categoryId());
            if (category == null) {
                throw new BudgetException("Unknown category: " + txn.categoryId());
            }
            if (category.name().equals(EXTRA_CATEGORY_NAME) && txn.reason().isBlank()) {
                throw new BudgetException("Extra expenses require a reason");
            }
        }
    }

    private Transaction findById(long id) {
        com.budgetguardian.datastructures.Iterator<Transaction> it = store.ledger().iterator();
        while (it.hasNext()) {
            Transaction txn = it.next();
            if (txn.id() == id) {
                return txn;
            }
        }
        throw new BudgetException("No such transaction: " + id);
    }

    /**
     * Computes — without mutating the store — the balance an account would
     * have after applying {@code txn} with {@code sign}. Used to write the
     * new balance to SQLite before memory is touched.
     */
    private Account previewBalance(Transaction txn, int sign) {
        Account account = store.accounts().get(txn.accountId());
        return account.withBalance(account.balanceSatang() + sign * signedEffect(txn));
    }

    /** @return the transaction's effect on its account: positive for income, negative otherwise. */
    private static long signedEffect(Transaction txn) {
        return txn.type() == TransactionType.INCOME ? txn.amountSatang() : -txn.amountSatang();
    }

    /**
     * Persists the balance change of swapping {@code remove} for {@code apply},
     * reading current balances from the (not yet mutated) store. Handles the
     * same-account case with one combined write — two independent previews
     * would each miss the other's delta.
     */
    private void persistSwapBalances(Transaction remove, Transaction apply) throws StorageException {
        if (remove.accountId().equals(apply.accountId())) {
            long balance = store.accounts().get(remove.accountId()).balanceSatang()
                    - signedEffect(remove) + signedEffect(apply);
            accounts.updateBalance(remove.accountId(), balance);
        } else {
            long removedBalance = store.accounts().get(remove.accountId()).balanceSatang()
                    - signedEffect(remove);
            long appliedBalance = store.accounts().get(apply.accountId()).balanceSatang()
                    + signedEffect(apply);
            accounts.updateBalance(remove.accountId(), removedBalance);
            accounts.updateBalance(apply.accountId(), appliedBalance);
        }
    }

    private void removeFromMemory(Transaction txn) {
        store.ledger().remove(txn);
        store.applyToBalance(txn, -1);
        store.applyToTotals(txn, -1);
        store.rebuildRecentTransactions();
    }

    private void swapInMemory(Transaction from, Transaction to) {
        store.ledger().remove(from);
        store.ledger().addLast(to);
        store.applyToBalance(from, -1);
        store.applyToTotals(from, -1);
        store.applyToBalance(to, +1);
        store.applyToTotals(to, +1);
        store.rebuildRecentTransactions();
    }

    private void publishLedgerEvents() {
        bus.publish(EventType.TRANSACTIONS_CHANGED);
        bus.publish(EventType.BALANCES_CHANGED);
    }
}
