package com.budgetguardian.service;

import com.budgetguardian.datastructures.CircularBuffer;
import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.datastructures.Graph;
import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.datastructures.Stack;
import com.budgetguardian.model.Account;
import com.budgetguardian.model.Bill;
import com.budgetguardian.model.Category;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtPayment;
import com.budgetguardian.model.RefillItem;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.model.Transfer;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * The single owner of all in-memory application state.
 *
 * <p><b>Purpose:</b> every custom data structure the app runs on lives here,
 * hydrated from SQLite once at startup by {@code StartupLoader} and mutated
 * only by services. The UI reads exclusively from this store — never from
 * the database.</p>
 *
 * <p><b>Contents and their structures:</b></p>
 * <ul>
 *   <li>{@code DoublyLinkedList} — transaction ledger and transfer history
 *       (chronological, O(1) tail append, reverse iteration for recent-first)</li>
 *   <li>{@code HashMap} — accounts, categories, debts, per-debt payment lists,
 *       refill items, bills, settings, and three derived total maps (O(1)
 *       reads)</li>
 *   <li>{@code CircularBuffer(20)} — recent-transactions widget</li>
 *   <li>{@code Stack} — undo actions, session-only</li>
 *   <li>{@code Graph} — transfer network, rebuilt from history on change</li>
 * </ul>
 *
 * <p><b>Derived totals</b> (all satang, expenses only) keep dashboard reads
 * O(1): daily totals (DailySpending category only) keyed {@code yyyy-MM-dd},
 * per-category month totals keyed {@code categoryId:yyyy-MM}, danger-spending
 * week totals keyed by the Monday of the calendar week (Mon–Sun rule).
 * {@link #applyToTotals} adds or removes one transaction's contribution with
 * {@code sign} ±1 — the single place the three maps are maintained, used by
 * add, delete, edit and undo alike.</p>
 *
 * <p><b>Space complexity:</b> O(n) in total row count.
 * Not thread-safe by design: all mutation happens on the JavaFX application
 * thread; the scheduler thread hands work over via {@code Platform.runLater}.</p>
 */
public final class DataStore {

    /** Only expenses under this category count toward the daily budget limit. */
    public static final String DAILY_SPENDING_CATEGORY = "DailySpending";

    private final DoublyLinkedList<Transaction> ledger = new DoublyLinkedList<>();
    private final DoublyLinkedList<Transfer> transfers = new DoublyLinkedList<>();
    private final HashMap<String, Account> accounts = new HashMap<>();
    private final HashMap<Integer, Category> categories = new HashMap<>();
    private final HashMap<Long, Debt> debts = new HashMap<>();
    private final HashMap<Long, DoublyLinkedList<DebtPayment>> debtPayments = new HashMap<>();
    private final HashMap<String, RefillItem> refillItems = new HashMap<>();
    private final HashMap<String, String> settings = new HashMap<>();
    private final HashMap<Long, Bill> bills = new HashMap<>();

    private final HashMap<String, Long> dailyTotals = new HashMap<>();
    private final HashMap<String, Long> otherDailyTotals = new HashMap<>();
    private final HashMap<String, Long> categoryMonthTotals = new HashMap<>();
    private final HashMap<String, Long> dangerWeekTotals = new HashMap<>();
    private final HashMap<String, Long> dailyOffsets = new HashMap<>();

    private final CircularBuffer<Transaction> recentTransactions = new CircularBuffer<>(20);
    private final Stack<Action> undoStack = new Stack<>();
    private Graph<String> transferGraph = new Graph<>();

    // ---- structure accessors -------------------------------------------------

    public DoublyLinkedList<Transaction> ledger() {
        return ledger;
    }

    public DoublyLinkedList<Transfer> transfers() {
        return transfers;
    }

    public HashMap<String, Account> accounts() {
        return accounts;
    }

    public HashMap<Integer, Category> categories() {
        return categories;
    }

    public HashMap<Long, Debt> debts() {
        return debts;
    }

    public HashMap<Long, DoublyLinkedList<DebtPayment>> debtPayments() {
        return debtPayments;
    }

    public HashMap<String, RefillItem> refillItems() {
        return refillItems;
    }

    public HashMap<String, String> settings() {
        return settings;
    }

    public HashMap<Long, Bill> bills() {
        return bills;
    }

    public CircularBuffer<Transaction> recentTransactions() {
        return recentTransactions;
    }

    public Stack<Action> undoStack() {
        return undoStack;
    }

    public Graph<String> transferGraph() {
        return transferGraph;
    }

    // ---- derived-total keys --------------------------------------------------

    /** @return daily-total key for a date: ISO {@code yyyy-MM-dd}. */
    public static String dayKey(LocalDate date) {
        return date.toString();
    }

    /** @return category-month key: {@code categoryId:yyyy-MM}. */
    public static String monthKey(int categoryId, LocalDate date) {
        return categoryId + ":" + date.getYear() + "-" + String.format("%02d", date.getMonthValue());
    }

    /** @return danger-week key: ISO date of the Monday of {@code date}'s week (Mon–Sun). */
    public static String weekKey(LocalDate date) {
        return date.with(DayOfWeek.MONDAY).toString();
    }

    // ---- derived-total reads (all O(1)) --------------------------------------

    /**
     * @return DailySpending-category spending on {@code date}, net of any
     *         receivable-debt repayments whose debt was incurred that day
     *         (money you fronted and got paid back no longer counts as usage).
     *         Clamped at zero. In satang.
     */
    public long dailyTotal(LocalDate date) {
        long raw = dailyTotals.getOrDefault(dayKey(date), 0L);
        long offset = dailyOffsets.getOrDefault(dayKey(date), 0L);
        return Math.max(0, raw - offset);
    }

    /** @return the gross DailySpending total on {@code date}, ignoring debt offsets. */
    public long dailyGrossTotal(LocalDate date) {
        return dailyTotals.getOrDefault(dayKey(date), 0L);
    }

    /**
     * Adds ({@code +}) or removes ({@code -}) a receivable-repayment offset
     * against {@code date}'s spending total. Keys prune at zero. O(1).
     */
    public void applyDailyOffset(LocalDate date, long delta) {
        addTo(dailyOffsets, dayKey(date), delta);
    }

    /** @return total expenses on {@code date} outside the DailySpending category, in satang. */
    public long otherDailyTotal(LocalDate date) {
        return otherDailyTotals.getOrDefault(dayKey(date), 0L);
    }

    /** @return total expenses of one category in {@code date}'s month, satang. */
    public long categoryMonthTotal(int categoryId, LocalDate date) {
        return categoryMonthTotals.getOrDefault(monthKey(categoryId, date), 0L);
    }

    /** @return combined danger-category spending in {@code date}'s Mon–Sun week, satang. */
    public long dangerWeekTotal(LocalDate date) {
        return dangerWeekTotals.getOrDefault(weekKey(date), 0L);
    }

    // ---- mutation helpers used by services and undo --------------------------

    /**
     * Adds ({@code sign = +1}) or removes ({@code sign = -1}) one expense's
     * contribution to the three derived total maps. Non-expense transactions
     * are ignored — income and withdrawals are not "spending". O(1).
     */
    public void applyToTotals(Transaction txn, int sign) {
        if (txn.type() != TransactionType.EXPENSE) {
            return;
        }
        long delta = sign * txn.amountSatang();
        Category category = categories.get(txn.categoryId());
        if (category != null) {
            if (category.name().equals(DAILY_SPENDING_CATEGORY)) {
                addTo(dailyTotals, dayKey(txn.date()), delta);
            } else {
                addTo(otherDailyTotals, dayKey(txn.date()), delta);
            }
        }
        addTo(categoryMonthTotals, monthKey(txn.categoryId(), txn.date()), delta);
        if (category != null && category.danger()) {
            addTo(dangerWeekTotals, weekKey(txn.date()), delta);
        }
    }

    /**
     * Applies a transaction's effect on its account balance:
     * expense/withdrawal subtract, income adds. {@code sign = -1} reverses
     * (undo). O(1). The caller persists the same balance to SQLite.
     *
     * @return the updated account
     */
    public Account applyToBalance(Transaction txn, int sign) {
        Account account = accounts.get(txn.accountId());
        long signed = txn.type() == TransactionType.INCOME ? txn.amountSatang() : -txn.amountSatang();
        Account updated = account.withBalance(account.balanceSatang() + sign * signed);
        accounts.put(updated.id(), updated);
        return updated;
    }

    /** Rebuilds the recent-transactions buffer from the ledger tail. O(n). */
    public void rebuildRecentTransactions() {
        recentTransactions.clear();
        Iterator<Transaction> it = ledger.iterator();
        while (it.hasNext()) {
            recentTransactions.add(it.next());   // capacity 20 keeps the newest
        }
    }

    /**
     * Rebuilds the transfer graph from transfer history: account ids as
     * vertices (even if isolated), one directed edge per transfer. O(V+E).
     */
    public void rebuildTransferGraph() {
        Graph<String> graph = new Graph<>();
        Iterator<String> accountIds = accounts.keys();
        while (accountIds.hasNext()) {
            graph.addVertex(accountIds.next());
        }
        Iterator<Transfer> it = transfers.iterator();
        while (it.hasNext()) {
            Transfer t = it.next();
            graph.addEdge(t.fromAccount(), t.toAccount(), t.amountSatang(),
                    t.reason() + " (" + t.date() + ")");
        }
        this.transferGraph = graph;
    }

    /** Recomputes all three derived total maps from the ledger. O(n). */
    public void rebuildTotals() {
        dailyTotals.clear();
        otherDailyTotals.clear();
        categoryMonthTotals.clear();
        dangerWeekTotals.clear();
        Iterator<Transaction> it = ledger.iterator();
        while (it.hasNext()) {
            applyToTotals(it.next(), +1);
        }
    }

    /**
     * Recomputes the receivable-repayment day offsets from every recorded debt
     * payment: each payment on a RECEIVABLE debt with an occurred date reduces
     * that day's spending. O(total payments). Call after debts and their
     * payments are loaded.
     */
    public void rebuildOffsets() {
        dailyOffsets.clear();
        Iterator<HashMap.Entry<Long, DoublyLinkedList<DebtPayment>>> it = debtPayments.iterator();
        while (it.hasNext()) {
            HashMap.Entry<Long, DoublyLinkedList<DebtPayment>> entry = it.next();
            Debt debt = debts.get(entry.key());
            if (debt == null || debt.direction() != DebtDirection.RECEIVABLE || debt.occurredDate() == null) {
                continue;
            }
            Iterator<DebtPayment> payments = entry.value().iterator();
            while (payments.hasNext()) {
                applyDailyOffset(debt.occurredDate(), payments.next().amountSatang());
            }
        }
    }

    private static void addTo(HashMap<String, Long> totals, String key, long delta) {
        long next = totals.getOrDefault(key, 0L) + delta;
        if (next == 0) {
            totals.remove(key);      // keep maps lean; absent means zero
        } else {
            totals.put(key, next);
        }
    }

    /**
     * @return the combined live key count of the four derived-total maps.
     *         Zero-valued keys are pruned by {@link #addTo}, so after every
     *         contribution has been reversed (undo/delete) this returns to 0 —
     *         the invariant a leak-guard test asserts, so repeated add/undo
     *         cycles can't grow the maps without bound.
     */
    int derivedTotalKeyCount() {
        return dailyTotals.size() + otherDailyTotals.size()
                + categoryMonthTotals.size() + dangerWeekTotals.size();
    }
}
