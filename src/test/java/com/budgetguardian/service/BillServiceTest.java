package com.budgetguardian.service;

import com.budgetguardian.model.Bill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Business logic for recurring bills, including the paid-as-transaction hand-off and undo. */
class BillServiceTest extends ServiceTestBase {

    private Bill newBill() {
        return new Bill(0, "Internet", 6_000, 15, null, NOW);
    }

    @Test
    void addStoresBillAndSurvivesRestart() {
        Bill saved = billService.add(newBill());
        assertTrue(saved.id() > 0);
        assertEquals(saved, store.bills().get(saved.id()));
        assertEquals(saved, reload().bills().get(saved.id()));
        assertEquals(1, store.undoStack().size());
    }

    @Test
    void payLogsBillCategoryExpenseAndAdvancesLastPaidDate() {
        Bill bill = billService.add(newBill());
        Bill paid = billService.pay(bill.id(), "SCB", 6_000, DAY, "monthly");

        assertEquals(DAY, paid.lastPaidDate());
        assertEquals(-6_000, store.accounts().get("SCB").balanceSatang());
        assertEquals(1, store.ledger().size());
        assertEquals(BILL, store.ledger().getFirst().categoryId());
        assertEquals("Internet", store.ledger().getFirst().itemName());
        assertEquals("monthly", store.ledger().getFirst().reason());
        // Paying is one undoable step (not two: AddTransaction + PayBill collapse into PayBill),
        // stacked on top of the AddBill action from creating the bill above.
        assertEquals(2, store.undoStack().size());
    }

    @Test
    void payAllowsACustomAmountDifferentFromTheDefault() {
        Bill bill = billService.add(newBill());
        billService.pay(bill.id(), "SCB", 7_500, DAY, "");
        assertEquals(-7_500, store.accounts().get("SCB").balanceSatang());
        assertEquals(7_500, store.ledger().getFirst().amountSatang());
    }

    @Test
    void undoPayRemovesTransactionAndRestoresLastPaidDate() {
        Bill bill = billService.add(newBill());
        billService.pay(bill.id(), "SCB", 6_000, DAY, "monthly");

        assertTrue(undoService.undo());
        assertTrue(store.ledger().isEmpty());
        assertEquals(0, store.accounts().get("SCB").balanceSatang());
        assertNull(store.bills().get(bill.id()).lastPaidDate());
        assertEquals(1, store.undoStack().size());   // the AddBill action remains
    }

    @Test
    void undoAddRemovesTheBill() {
        Bill bill = billService.add(newBill());
        assertTrue(undoService.undo());
        assertNull(store.bills().get(bill.id()));
        assertTrue(reload().bills().isEmpty());
    }

    @Test
    void deleteRemovesTheBillButKeepsPastPayments() {
        Bill bill = billService.add(newBill());
        billService.pay(bill.id(), "SCB", 6_000, DAY, "monthly");
        billService.delete(bill.id());
        assertNull(store.bills().get(bill.id()));
        assertEquals(1, store.ledger().size());   // the payment stays in the ledger
    }

    @Test
    void oneOffBillHasNoPaydayAndIsNeverDue() {
        Bill oneOff = billService.add(new Bill(0, "Repair", 2_000, null, null, NOW));
        assertTrue(!oneOff.isRecurring());
        assertTrue(!oneOff.isDue(DAY.plusYears(1)));
    }

    @Test
    void recurringBillIsDueOncePaydayReachedAndNotPaidThisMonth() {
        Bill bill = billService.add(new Bill(0, "Internet", 6_000, 15, null, NOW));
        assertTrue(!bill.isDue(DAY.withDayOfMonth(14)));   // before payday
        assertTrue(bill.isDue(DAY.withDayOfMonth(16)));    // past payday, unpaid

        Bill paid = billService.pay(bill.id(), "SCB", 6_000, DAY.withDayOfMonth(16), "");
        assertTrue(!paid.isDue(DAY.withDayOfMonth(20)));   // paid this month
        assertTrue(paid.isDue(DAY.withDayOfMonth(16).plusMonths(1)));   // next month, unpaid again
    }
}
