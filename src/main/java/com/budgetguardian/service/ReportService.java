package com.budgetguardian.service;

import com.budgetguardian.algorithm.sorting.HeapSort;
import com.budgetguardian.datastructures.Comparator;
import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Category;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtStatus;
import com.budgetguardian.model.RefillItem;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Read-only analytics over the in-memory ledger — no database access, no
 * mutation. Produces immutable report records the {@code ReportsView} renders
 * as tables and charts.
 *
 * <p><b>Algorithm choices (documented per requirement):</b> category rankings
 * are ordered with {@link HeapSort} (O(n log n), demonstrating the heap reuse
 * from {@code PriorityQueue}); period aggregation is a single O(n) ledger
 * scan into a {@code DynamicArray}.</p>
 */
public final class ReportService {

    /** Total, transaction count and daily average over a date range. */
    public record PeriodSummary(LocalDate from, LocalDate to, long totalSatang,
                                int transactionCount, long averagePerDaySatang) {
    }

    /** One category's spend over the reporting window. */
    public record CategoryTotal(int categoryId, String name, boolean danger, long totalSatang) {
    }

    /** One Mon–Sun week's total for a single category. */
    public record WeeklyTotal(LocalDate weekStart, long totalSatang) {
    }

    /** Outstanding and settled debt figures. */
    public record DebtReport(long outstandingPayableSatang, long outstandingReceivableSatang,
                             int openCount, int settledCount) {
    }

    /** One gambling result: the ledger row it came from and which way it went. */
    public record GamblingEntry(Transaction txn, boolean win) {

        /** @return the day the win or loss happened. */
        public LocalDate date() {
            return txn.date();
        }

        /** @return the staked or won amount, always positive. */
        public long amountSatang() {
            return txn.amountSatang();
        }
    }

    /** Win/loss totals and counts over a set of gambling entries. */
    public record GamblingSummary(long wonSatang, long lostSatang, int winCount, int lossCount,
                                  long biggestWinSatang, long biggestLossSatang) {

        /** @return wins minus losses; negative when gambling is down overall. */
        public long netSatang() {
            return wonSatang - lostSatang;
        }
    }

    private final DataStore store;

    public ReportService(DataStore store) {
        this.store = store;
    }

    /**
     * @return spending summary over {@code [from, to]} inclusive (expenses only)
     */
    public PeriodSummary summary(LocalDate from, LocalDate to) {
        long total = 0;
        int count = 0;
        Iterator<Transaction> it = store.ledger().iterator();
        while (it.hasNext()) {
            Transaction txn = it.next();
            if (txn.type() == TransactionType.EXPENSE && inRange(txn.date(), from, to)) {
                total += txn.amountSatang();
                count++;
            }
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        long average = days > 0 ? total / days : 0;
        return new PeriodSummary(from, to, total, count, average);
    }

    /** @return summary for the Mon–Sun week containing {@code anyDay}. */
    public PeriodSummary weekly(LocalDate anyDay) {
        LocalDate monday = anyDay.with(DayOfWeek.MONDAY);
        return summary(monday, monday.plusDays(6));
    }

    /** @return summary for the calendar month containing {@code anyDay}. */
    public PeriodSummary monthly(LocalDate anyDay) {
        LocalDate first = anyDay.withDayOfMonth(1);
        return summary(first, first.plusMonths(1).minusDays(1));
    }

    /** @return summary for the calendar year containing {@code anyDay}. */
    public PeriodSummary yearly(LocalDate anyDay) {
        LocalDate first = anyDay.withDayOfYear(1);
        return summary(first, first.plusYears(1).minusDays(1));
    }

    /**
     * @return per-category totals over {@code [from, to]}, sorted by amount
     *         descending with heap sort. Categories with zero spend are omitted.
     */
    public DynamicArray<CategoryTotal> categoryTotals(LocalDate from, LocalDate to) {
        // Accumulate per category id in a fixed small array indexed by id.
        long[] totals = new long[128];
        Iterator<Transaction> it = store.ledger().iterator();
        while (it.hasNext()) {
            Transaction txn = it.next();
            if (txn.type() == TransactionType.EXPENSE && inRange(txn.date(), from, to)
                    && txn.categoryId() != null && txn.categoryId() < totals.length) {
                totals[txn.categoryId()] += txn.amountSatang();
            }
        }
        DynamicArray<CategoryTotal> rows = new DynamicArray<>();
        for (int id = 0; id < totals.length; id++) {
            if (totals[id] > 0) {
                Category category = store.categories().get(id);
                String name = category != null ? category.name() : "#" + id;
                boolean danger = category != null && category.danger();
                rows.append(new CategoryTotal(id, name, danger, totals[id]));
            }
        }
        Comparator<CategoryTotal> byAmountDesc = (a, b) -> Long.compare(a.totalSatang(), b.totalSatang());
        // HeapSort ascending by amount, then the top category is the last row;
        // reverse into descending for display.
        new HeapSort<CategoryTotal>().sort(rows, byAmountDesc);
        reverse(rows);
        return rows;
    }

    /**
     * @return the highest-spending category over the window, or null if there
     *         was no spending
     */
    public CategoryTotal highestCategory(LocalDate from, LocalDate to) {
        DynamicArray<CategoryTotal> rows = categoryTotals(from, to);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** @return combined danger-category (alcohol + gambling) spend over the window. */
    public long dangerTotal(LocalDate from, LocalDate to) {
        long total = 0;
        DynamicArray<CategoryTotal> rows = categoryTotals(from, to);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).danger()) {
                total += rows.get(i).totalSatang();
            }
        }
        return total;
    }

    /**
     * @return the last {@code weeks} Mon–Sun weekly totals for one category
     *         by name (oldest first, including the week containing {@code anyDay})
     */
    public DynamicArray<WeeklyTotal> weeklyCategoryTrend(String categoryName, LocalDate anyDay, int weeks) {
        LocalDate thisMonday = anyDay.with(DayOfWeek.MONDAY);
        DynamicArray<WeeklyTotal> rows = new DynamicArray<>();
        for (int i = weeks - 1; i >= 0; i--) {
            LocalDate weekStart = thisMonday.minusWeeks(i);
            rows.append(new WeeklyTotal(weekStart, categoryTotalInRange(categoryName, weekStart, weekStart.plusDays(6))));
        }
        return rows;
    }

    private long categoryTotalInRange(String categoryName, LocalDate from, LocalDate to) {
        long total = 0;
        Iterator<Transaction> it = store.ledger().iterator();
        while (it.hasNext()) {
            Transaction txn = it.next();
            if (txn.type() != TransactionType.EXPENSE || !inRange(txn.date(), from, to) || txn.categoryId() == null) {
                continue;
            }
            Category category = store.categories().get(txn.categoryId());
            if (category != null && category.name().equals(categoryName)) {
                total += txn.amountSatang();
            }
        }
        return total;
    }

    /** @return outstanding and settled debt figures across all debts. */
    public DebtReport debtReport() {
        long payable = 0;
        long receivable = 0;
        int open = 0;
        int settled = 0;
        Iterator<Debt> it = store.debts().values();
        while (it.hasNext()) {
            Debt debt = it.next();
            if (debt.status() == DebtStatus.SETTLED) {
                settled++;
                continue;
            }
            open++;
            long remaining = debt.amountSatang() - paidTotal(debt.id());
            if (debt.direction() == DebtDirection.PAYABLE) {
                payable += remaining;
            } else {
                receivable += remaining;
            }
        }
        return new DebtReport(payable, receivable, open, settled);
    }

    /**
     * @return every gambling result in the ledger, newest first. Wins are
     *         income rows whose reason contains
     *         {@link TransactionService#GAMBLING_WIN_REASON}; losses are
     *         expenses in the {@code Gamble} category. O(n) ledger scan.
     */
    public DynamicArray<GamblingEntry> gamblingLog() {
        DynamicArray<GamblingEntry> rows = new DynamicArray<>();
        Iterator<Transaction> it = store.ledger().descendingIterator();
        while (it.hasNext()) {
            Transaction txn = it.next();
            if (isGamblingWin(txn)) {
                rows.append(new GamblingEntry(txn, true));
            } else if (isGamblingLoss(txn)) {
                rows.append(new GamblingEntry(txn, false));
            }
        }
        return rows;
    }

    /** @return totals over {@code entries} (typically {@link #gamblingLog()}). */
    public GamblingSummary gamblingSummary(DynamicArray<GamblingEntry> entries) {
        long won = 0;
        long lost = 0;
        int wins = 0;
        int losses = 0;
        long biggestWin = 0;
        long biggestLoss = 0;
        for (int i = 0; i < entries.size(); i++) {
            GamblingEntry entry = entries.get(i);
            long amount = entry.amountSatang();
            if (entry.win()) {
                won += amount;
                wins++;
                biggestWin = Math.max(biggestWin, amount);
            } else {
                lost += amount;
                losses++;
                biggestLoss = Math.max(biggestLoss, amount);
            }
        }
        return new GamblingSummary(won, lost, wins, losses, biggestWin, biggestLoss);
    }

    private boolean isGamblingWin(Transaction txn) {
        return txn.type() == TransactionType.INCOME
                && txn.reason() != null
                && txn.reason().toLowerCase(java.util.Locale.ROOT)
                        .contains(TransactionService.GAMBLING_WIN_REASON);
    }

    private boolean isGamblingLoss(Transaction txn) {
        if (txn.type() != TransactionType.EXPENSE || txn.categoryId() == null) {
            return false;
        }
        Category category = store.categories().get(txn.categoryId());
        return category != null && category.name().equals(TransactionService.GAMBLE_CATEGORY_NAME);
    }

    /** @return refillable items overdue on {@code today}. */
    public DynamicArray<RefillItem> overdueRefills(LocalDate today) {
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

    private long paidTotal(long debtId) {
        var payments = store.debtPayments().get(debtId);
        long total = 0;
        if (payments != null) {
            Iterator<com.budgetguardian.model.DebtPayment> it = payments.iterator();
            while (it.hasNext()) {
                total += it.next().amountSatang();
            }
        }
        return total;
    }

    private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    private static <T> void reverse(DynamicArray<T> array) {
        int i = 0;
        int j = array.size() - 1;
        while (i < j) {
            T tmp = array.get(i);
            array.set(i, array.get(j));
            array.set(j, tmp);
            i++;
            j--;
        }
    }
}
