package com.budgetguardian.service;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtStatus;
import com.budgetguardian.service.ReportService.CategoryTotal;
import com.budgetguardian.service.ReportService.DebtReport;
import com.budgetguardian.service.ReportService.PeriodSummary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Aggregation correctness of {@link ReportService}. */
class ReportServiceTest extends ServiceTestBase {

    private ReportService reports() {
        return new ReportService(store);
    }

    @Test
    void periodSummarySumsExpensesOnly() {
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 10_000, DAY));
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 5_000, DAY.plusDays(2)));
        transactionService.add(income("SCB", 100_000, DAY));            // excluded
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 999, DAY.plusDays(40)));  // out of range

        PeriodSummary s = reports().summary(DAY, DAY.plusDays(6));
        assertEquals(15_000, s.totalSatang());
        assertEquals(2, s.transactionCount());
        assertEquals(15_000 / 7, s.averagePerDaySatang());
    }

    @Test
    void weeklyMonthlyYearlyWindows() {
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 1_000, DAY));            // Mon 2026-07-06
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 2_000, DAY.plusDays(6))); // Sun, same week
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 4_000, DAY.plusDays(20))); // same month
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 8_000, DAY.plusMonths(3))); // same year

        assertEquals(3_000, reports().weekly(DAY).totalSatang());
        assertEquals(7_000, reports().monthly(DAY).totalSatang());
        assertEquals(15_000, reports().yearly(DAY).totalSatang());
    }

    @Test
    void categoryTotalsSortedDescendingByHeapSort() {
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 3_000, DAY));
        transactionService.add(expense("SCB", ALCOHOL, null, 9_000, DAY));
        transactionService.add(expense("SCB", GAMBLE, null, 5_000, DAY));
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 1_000, DAY));   // Food now 4000

        DynamicArray<CategoryTotal> totals = reports().categoryTotals(DAY, DAY);
        assertEquals(3, totals.size());
        assertEquals(ALCOHOL, totals.get(0).categoryId());   // 9000 highest
        assertEquals(9_000, totals.get(0).totalSatang());
        assertEquals(GAMBLE, totals.get(1).categoryId());  // 5000
        assertEquals(DAILY_SPENDING, totals.get(2).categoryId());      // 4000
    }

    @Test
    void highestCategoryAndDangerTotal() {
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 3_000, DAY));
        transactionService.add(expense("SCB", ALCOHOL, null, 9_000, DAY));
        transactionService.add(expense("SCB", GAMBLE, null, 5_000, DAY));

        assertEquals(ALCOHOL, reports().highestCategory(DAY, DAY).categoryId());
        assertEquals(14_000, reports().dangerTotal(DAY, DAY));   // alcohol + gambling
    }

    @Test
    void emptyWindowHasNoHighestCategory() {
        assertNull(reports().highestCategory(DAY, DAY));
        assertTrue(reports().categoryTotals(DAY, DAY).isEmpty());
        assertEquals(0, reports().dangerTotal(DAY, DAY));
    }

    @Test
    void debtReportSumsOutstandingByDirection() {
        Debt payable = debtService.add(new Debt(0, DebtDirection.PAYABLE, "Alice", 100_000,
                null, DebtStatus.OPEN, null, NOW));
        debtService.pay(payable.id(), "SCB", 30_000, DAY);            // 70000 remaining
        debtService.add(new Debt(0, DebtDirection.RECEIVABLE, "Bob", 50_000,
                null, DebtStatus.OPEN, null, NOW));
        Debt settled = debtService.add(new Debt(0, DebtDirection.PAYABLE, "Carol", 10_000,
                null, DebtStatus.OPEN, null, NOW));
        debtService.pay(settled.id(), "SCB", 10_000, DAY);           // settles

        DebtReport report = reports().debtReport();
        assertEquals(70_000, report.outstandingPayableSatang());
        assertEquals(50_000, report.outstandingReceivableSatang());
        assertEquals(2, report.openCount());
        assertEquals(1, report.settledCount());
    }
}
