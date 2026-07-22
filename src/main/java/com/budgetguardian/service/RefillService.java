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
 * controller calls {@link #track}. If the item is already confirmed
 * refillable, its purchase is recorded silently. Otherwise the ledger is
 * scanned (linear search — the item index is the ledger itself) for an
 * earlier purchase of the same normalized name; if one exists, the item is
 * auto-confirmed refillable immediately — no "keep as refillable?" prompt,
 * repeat purchases of the same item are tracked silently from the second
 * occurrence on.</p>
 *
 * <p><b>Time complexity:</b> track O(n) ledger scan on first repeat, O(1)
 * once known; overdue scan O(r) items.</p>
 */
public final class RefillService {

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
     * Called after an expense was recorded. Three outcomes:
     * <ul>
     *   <li>item already refillable → purchase recorded silently</li>
     *   <li>item bought before but not tracked yet → auto-confirmed refillable
     *       now, using the observed gap as its initial interval</li>
     *   <li>first purchase or no item name → nothing happens</li>
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
        if (previous == null) {
            return null;
        }
        long gap = Math.max(1, ChronoUnit.DAYS.between(previous, justRecorded.date()));
        return confirm(name, gap, justRecorded.date());
    }

    /**
     * Auto-tracks a newly detected repeat item, storing it permanently with
     * the observed gap as its initial interval, and pushes an undo action.
     *
     * @return the stored item
     */
    private RefillItem confirm(String itemName, long gapDays, LocalDate purchaseDate) {
        RefillItem item = new RefillItem(itemName, gapDays, purchaseDate, 2);
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
