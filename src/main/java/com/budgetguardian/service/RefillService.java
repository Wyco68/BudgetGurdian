package com.budgetguardian.service;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.RefillItem;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.repository.RefillRepository;
import com.budgetguardian.repository.TransactionRunner;

import com.budgetguardian.repository.StorageException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Refillable-item detection and reminders.
 *
 * <p><b>Flow:</b> after an expense with an item name is recorded, the
 * controller calls {@link #track}. If the item is already tracked, its
 * purchase is recorded silently and the running-average interval updates.
 * Otherwise:</p>
 * <ul>
 *   <li><b>Refill-category expenses</b> are tracked from the very first
 *       purchase — choosing that category <em>is</em> the user declaring the
 *       item refillable, so it appears in the Refills screen immediately with
 *       a provisional {@value #DEFAULT_INTERVAL_DAYS}-day interval that the
 *       second purchase replaces with the real observed gap.</li>
 *   <li><b>Other categories</b> are tracked only once a repeat purchase of the
 *       same normalized name is found in the ledger (linear search), using the
 *       observed gap as the initial interval.</li>
 * </ul>
 *
 * <p><b>Time complexity:</b> track O(n) ledger scan on first repeat, O(1)
 * once known; overdue scan O(r) items.</p>
 */
public final class RefillService {

    /** Provisional interval for a first-purchase Refill item, until a real gap is observed. */
    static final int DEFAULT_INTERVAL_DAYS = 30;

    private final DataStore store;
    private final EventBus bus;
    private final TransactionRunner runner;
    private final RefillRepository refills;

    public RefillService(DataStore store, EventBus bus, TransactionRunner runner,
                         RefillRepository refills) {
        this.store = store;
        this.bus = bus;
        this.runner = runner;
        this.refills = refills;
    }

    /** @return lowercase, trimmed form used as the map key. */
    public static String normalize(String itemName) {
        return itemName == null ? null : itemName.trim().toLowerCase();
    }

    /**
     * Called after an expense was recorded. Outcomes:
     * <ul>
     *   <li>item already tracked → purchase recorded silently</li>
     *   <li>Refill-category expense, first purchase → tracked immediately with
     *       a provisional interval</li>
     *   <li>other category, repeat purchase found in the ledger → tracked now,
     *       using the observed gap as its initial interval</li>
     *   <li>otherwise (first non-Refill purchase, or no item name) → nothing</li>
     * </ul>
     *
     * @param justRecorded the expense that was just added (already in the ledger)
     * @return the tracked item, or null when nothing was tracked
     */
    public RefillItem track(Transaction justRecorded) {
        String name = normalize(justRecorded.itemName());
        if (name == null || name.isEmpty() || justRecorded.type() != TransactionType.EXPENSE) {
            return null;
        }
        RefillItem known = store.refillItems().get(name);
        if (known != null) {
            return recordKnownPurchase(known, justRecorded.date());
        }
        LocalDate previous = findPreviousPurchase(name, justRecorded);
        if (previous != null) {
            long gap = Math.max(1, ChronoUnit.DAYS.between(previous, justRecorded.date()));
            return confirm(name, gap, justRecorded.date(), 2);
        }
        if (isRefillCategory(justRecorded)) {
            return confirm(name, DEFAULT_INTERVAL_DAYS, justRecorded.date(), 1);
        }
        return null;
    }

    /** @return whether the expense is filed under the Refill category. */
    private boolean isRefillCategory(Transaction txn) {
        if (txn.categoryId() == null) {
            return false;
        }
        var category = store.categories().get(txn.categoryId());
        return category != null && category.name().equals(TransactionService.REFILL_CATEGORY_NAME);
    }

    /**
     * Tracks a newly detected item, storing it permanently with
     * {@code intervalDays} as its initial interval, and pushes an undo action.
     *
     * @return the stored item
     */
    private RefillItem confirm(String itemName, long intervalDays, LocalDate purchaseDate, int purchaseCount) {
        RefillItem item = new RefillItem(itemName, intervalDays, purchaseDate, purchaseCount);
        try {
            runner.run(() -> {
                refills.upsert(item);
                return null;
            });
        } catch (StorageException e) {
            throw new BudgetException("Failed to save refill item", e);
        }
        store.refillItems().put(item.name(), item);
        store.undoStack().push(new Action.ConfirmRefill(item));
        bus.publish(EventType.REFILLS_CHANGED);
        return item;
    }

    /** @return items overdue for refill on {@code today}, in map order. O(r). */
    public DynamicArray<RefillItem> overdueItems(LocalDate today) {
        DynamicArray<RefillItem> overdue = new DynamicArray<>();
        Iterator<RefillItem> it = store.refillItems().values();
        while (it.hasNext()) {
            RefillItem item = it.next();
            if (item.isOverdue(today)) {
                overdue.append(item);
            }
        }
        return overdue;
    }

    /** Inverse of {@link #confirm}: forgets the item; next duplicate asks again. */
    void undoConfirm(RefillItem item) {
        try {
            runner.run(() -> {
                refills.delete(item.name());
                return null;
            });
        } catch (StorageException e) {
            throw new BudgetException("Undo failed", e);
        }
        store.refillItems().remove(item.name());
        bus.publish(EventType.REFILLS_CHANGED);
    }

    /**
     * Updates a confirmed item's running-average interval for a new purchase.
     *
     * @return the updated item, or {@code known} unchanged for a same-day
     *         rebuy or backdated entry
     */
    private RefillItem recordKnownPurchase(RefillItem known, LocalDate purchaseDate) {
        if (!purchaseDate.isAfter(known.lastPurchase())) {
            return known;    // same-day rebuy or backdated entry — interval unchanged
        }
        RefillItem updated = known.recordPurchase(purchaseDate);
        try {
            runner.run(() -> {
                refills.upsert(updated);
                return null;
            });
        } catch (StorageException e) {
            throw new BudgetException("Failed to update refill item", e);
        }
        store.refillItems().put(updated.name(), updated);
        bus.publish(EventType.REFILLS_CHANGED);
        return updated;
    }

    /**
     * Linear search (newest first) for the latest earlier expense with the
     * same normalized item name, skipping the just-recorded transaction.
     */
    private LocalDate findPreviousPurchase(String normalizedName, Transaction justRecorded) {
        Iterator<Transaction> it = store.ledger().descendingIterator();
        while (it.hasNext()) {
            Transaction txn = it.next();
            if (txn.id() == justRecorded.id() || txn.type() != TransactionType.EXPENSE) {
                continue;
            }
            if (normalizedName.equals(normalize(txn.itemName()))
                    && !txn.date().isAfter(justRecorded.date())) {
                return txn.date();
            }
        }
        return null;
    }
}
