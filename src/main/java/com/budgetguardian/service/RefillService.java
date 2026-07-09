package com.budgetguardian.service;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.RefillItem;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.repository.RefillRepository;
import com.budgetguardian.repository.TransactionRunner;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Refillable-item detection and reminders.
 *
 * <p><b>Flow:</b> after an expense with an item name is recorded, the
 * controller calls {@link #detectDuplicate}. If the item is already
 * confirmed refillable, its purchase is recorded silently. Otherwise the
 * ledger is scanned (linear search — the item index is the ledger itself)
 * for an earlier purchase of the same normalized name; if one exists, a
 * {@link RefillPrompt} is returned and the UI asks <em>"Keep as
 * refillable?"</em>. "Yes" → {@link #confirm} persists the item permanently.
 * "No" → nothing is stored, so the question naturally comes back on the next
 * duplicate (per requirements, only "Yes" is remembered).</p>
 *
 * <p><b>Time complexity:</b> detect O(n) ledger scan; confirm/record O(1);
 * overdue scan O(r) items.</p>
 */
public final class RefillService {

    /**
     * Request for the "keep as refillable?" dialog.
     *
     * @param itemName     normalized item name
     * @param lastPurchase date of the previous purchase found in the ledger
     * @param gapDays      days between the previous and the current purchase
     */
    public record RefillPrompt(String itemName, LocalDate lastPurchase, long gapDays) {
    }

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
     *   <li>item already refillable → purchase recorded, returns null</li>
     *   <li>item bought before but not confirmed → returns a prompt</li>
     *   <li>first purchase or no item name → returns null</li>
     * </ul>
     *
     * @param justRecorded the expense that was just added (already in the ledger)
     * @return a prompt for the UI, or null when no question is needed
     */
    public RefillPrompt detectDuplicate(Transaction justRecorded) {
        String name = normalize(justRecorded.itemName());
        if (name == null || name.isEmpty() || justRecorded.type() != TransactionType.EXPENSE) {
            return null;
        }
        RefillItem known = store.refillItems().get(name);
        if (known != null) {
            recordKnownPurchase(known, justRecorded.date());
            return null;
        }
        LocalDate previous = findPreviousPurchase(name, justRecorded);
        if (previous == null) {
            return null;
        }
        long gap = Math.max(1, ChronoUnit.DAYS.between(previous, justRecorded.date()));
        return new RefillPrompt(name, previous, gap);
    }

    /**
     * User answered "Yes": stores the item permanently with the observed gap
     * as its initial interval, and pushes an undo action.
     *
     * @return the stored item
     */
    public RefillItem confirm(RefillPrompt prompt, LocalDate purchaseDate) {
        RefillItem item = new RefillItem(prompt.itemName(), prompt.gapDays(), purchaseDate, 2);
        try {
            runner.run(() -> {
                refills.upsert(item);
                return null;
            });
        } catch (SQLException e) {
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
        } catch (SQLException e) {
            throw new BudgetException("Undo failed", e);
        }
        store.refillItems().remove(item.name());
        bus.publish(EventType.REFILLS_CHANGED);
    }

    /** Updates a confirmed item's running-average interval for a new purchase. */
    private void recordKnownPurchase(RefillItem known, LocalDate purchaseDate) {
        if (!purchaseDate.isAfter(known.lastPurchase())) {
            return;    // same-day rebuy or backdated entry — interval unchanged
        }
        RefillItem updated = known.recordPurchase(purchaseDate);
        try {
            runner.run(() -> {
                refills.upsert(updated);
                return null;
            });
        } catch (SQLException e) {
            throw new BudgetException("Failed to update refill item", e);
        }
        store.refillItems().put(updated.name(), updated);
        bus.publish(EventType.REFILLS_CHANGED);
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
