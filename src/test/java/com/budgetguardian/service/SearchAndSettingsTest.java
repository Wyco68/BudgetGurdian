package com.budgetguardian.service;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtStatus;
import com.budgetguardian.model.Transaction;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for {@link SearchService} and {@link SettingsService}. */
class SearchAndSettingsTest extends ServiceTestBase {

    @Test
    void freeTextSearchMatchesReasonItemAccountAndCategory() {
        transactionService.add(new Transaction(0, com.budgetguardian.model.TransactionType.EXPENSE,
                "SCB", DAILY_SPENDING, "Bread", 3_000, "weekly groceries", DAY, NOW));
        transactionService.add(expense("SAVING", ALCOHOL, null, 5_000, DAY));

        assertEquals(1, searchService.searchTransactions("grocer").size());   // reason
        assertEquals(1, searchService.searchTransactions("BREAD").size());    // item, case-insensitive
        assertEquals(1, searchService.searchTransactions("saving").size());   // account id
        assertEquals(1, searchService.searchTransactions("alcohol").size());  // category name
        assertTrue(searchService.searchTransactions("nothing-here").isEmpty());
        assertTrue(searchService.searchTransactions("  ").isEmpty());
    }

    @Test
    void searchReturnsNewestFirst() {
        transactionService.add(expense("SCB", DAILY_SPENDING, "a", 100, DAY));
        transactionService.add(expense("SCB", DAILY_SPENDING, "b", 200, DAY.plusDays(1)));
        DynamicArray<Transaction> hits = searchService.searchTransactions("test");
        assertEquals(2, hits.size());
        assertEquals(200, hits.get(0).amountSatang());     // newest first
    }

    @Test
    void filtersByAccountCategoryAndDateRange() {
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 100, DAY));
        transactionService.add(expense("SAVING", DAILY_SPENDING, null, 200, DAY.plusDays(1)));
        transactionService.add(expense("SCB", ALCOHOL, null, 300, DAY.plusDays(2)));

        assertEquals(2, searchService.byAccount("SCB").size());
        assertEquals(2, searchService.byCategory(DAILY_SPENDING).size());
        assertEquals(2, searchService.byDateRange(DAY, DAY.plusDays(1)).size());
        assertEquals(1, searchService.byDateRange(DAY.plusDays(2), DAY.plusDays(9)).size());
    }

    @Test
    void debtSearchByPerson() {
        debtService.add(new Debt(0, DebtDirection.PAYABLE, "Alice Smith", 1_000,
                null, DebtStatus.OPEN, null, NOW));
        debtService.add(new Debt(0, DebtDirection.RECEIVABLE, "Bob", 2_000,
                null, DebtStatus.OPEN, null, NOW));
        assertEquals(1, searchService.searchDebts("alice").size());
        assertTrue(searchService.searchDebts("charlie").isEmpty());
    }

    @Test
    void settingsTypedDefaultsFromSeed() {
        assertEquals(18_000, settingsService.dailyBudgetSatang());       // 180 THB
        assertEquals(20_000, settingsService.dangerWeeklyLimitSatang()); // 200 THB
        assertEquals(LocalTime.of(20, 0), settingsService.reminderTime());
    }

    @Test
    void settingWritesPersistAcrossRestart() {
        settingsService.set(SettingsService.DAILY_BUDGET, "25000");
        assertEquals(25_000, settingsService.dailyBudgetSatang());
        assertEquals("25000", reload().settings().get(SettingsService.DAILY_BUDGET));
    }

    @Test
    void settingRejectsBlankKeyOrValue() {
        assertThrows(BudgetException.class, () -> settingsService.set(" ", "x"));
        assertThrows(BudgetException.class, () -> settingsService.set("k", " "));
    }
}
