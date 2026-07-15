package com.budgetguardian.integration;

import com.budgetguardian.database.DatabaseManager;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtStatus;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.model.Transfer;
import com.budgetguardian.service.NotificationType;
import com.budgetguardian.service.ServiceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of a realistic session driven entirely through
 * {@link ServiceContext} (the same object graph the app builds), against a
 * temp-file database. Verifies balances, spending totals, the transfer graph,
 * notifications and full restart-consistency.
 */
class FullStackIntegrationTest {

    private static final LocalDate MON = LocalDate.of(2026, 7, 6);   // a Monday
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 9, 0);

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private ServiceContext ctx;

    @BeforeEach
    void open() throws Exception {
        db = new DatabaseManager(tempDir.resolve("integration.db"));
        db.open();
        ctx = new ServiceContext(db.getConnection(), () -> MON);
    }

    @AfterEach
    void close() throws Exception {
        db.close();
    }

    private Transaction expense(String account, int category, String item, long satang, LocalDate date) {
        return new Transaction(0, TransactionType.EXPENSE, account, category, item, satang, "x", date, NOW);
    }

    @Test
    void realisticSessionThenRestartStaysConsistent() throws Exception {
        // Income lands in Saving.
        ctx.transactions().add(new Transaction(0, TransactionType.INCOME, "SAVING", null, null,
                1_000_000, "scholarship", MON, NOW));
        // A few expenses on SCB.
        ctx.transactions().add(expense("SCB", 1, "Bread", 5_000, MON));           // Food
        ctx.transactions().add(expense("SCB", 1, "Bread", 5_000, MON.plusDays(7))); // repeat → refillable
        ctx.transactions().add(expense("SCB", 10, null, 12_000, MON));            // Alcohol
        ctx.transactions().add(expense("SCB", 11, null, 15_000, MON));            // Gambling → danger 27000 > 20000
        // Transfer Saving → SCB.
        ctx.transfers().add(new Transfer(0, "SAVING", "SCB", 200_000, "top up", MON, NOW));
        // Debt: owe Alice 100000, pay 40000.
        Debt debt = ctx.debts().add(new Debt(0, DebtDirection.PAYABLE, "Alice", 100_000,
                MON.plusDays(30), DebtStatus.OPEN, null, NOW));
        ctx.debts().pay(debt.id(), "SCB", 40_000, MON);

        // Danger banner should be the top alert (priority 100).
        assertNotNull(ctx.notifications().heroBanner());
        assertEquals(NotificationType.DANGER_SPENDING, ctx.notifications().heroBanner().type());

        // Balances: SCB = -5000-5000-12000-15000 +200000 -40000 = 123000
        assertEquals(123_000, ctx.store().accounts().get("SCB").balanceSatang());
        // SAVING = +1,000,000 -200,000 = 800,000
        assertEquals(800_000, ctx.store().accounts().get("SAVING").balanceSatang());
        // Danger week total = 27000
        assertEquals(27_000, ctx.store().dangerWeekTotal(MON));
        // Graph: one Saving→SCB edge.
        assertEquals(200_000, ctx.store().transferGraph().totalFlow("SAVING", "SCB"));
        assertTrue(ctx.store().transferGraph().hasPath("SAVING", "SCB"));

        // Confirm the refillable item detected on the repeat purchase.
        var repeat = lastBreadPurchase();
        var prompt = ctx.refills().detectDuplicate(repeat);
        assertNotNull(prompt);
        ctx.refills().confirm(prompt, repeat.date());
        assertNotNull(ctx.store().refillItems().get("bread"));

        // Restart: rebuild a fresh context from the same DB.
        ServiceContext restarted = new ServiceContext(db.getConnection(), () -> MON);
        assertEquals(123_000, restarted.store().accounts().get("SCB").balanceSatang());
        assertEquals(800_000, restarted.store().accounts().get("SAVING").balanceSatang());
        assertEquals(27_000, restarted.store().dangerWeekTotal(MON));
        assertEquals(200_000, restarted.store().transferGraph().totalFlow("SAVING", "SCB"));
        assertEquals(1, restarted.store().debtPayments().get(debt.id()).size());
        assertNotNull(restarted.store().refillItems().get("bread"));
        assertTrue(restarted.store().undoStack().isEmpty());   // undo is session-only
    }

    @Test
    void undoUnwindsEntireSessionToFreshState() {
        ctx.transactions().add(expense("SCB", 1, null, 5_000, MON));
        ctx.transfers().add(new Transfer(0, "SCB", "SAVING", 1_000, "m", MON, NOW));
        Debt debt = ctx.debts().add(new Debt(0, DebtDirection.RECEIVABLE, "Bob", 10_000,
                null, DebtStatus.OPEN, null, NOW));
        ctx.debts().pay(debt.id(), "SCB", 10_000, MON);

        while (ctx.undo().canUndo()) {
            ctx.undo().undo();
        }
        assertTrue(ctx.store().ledger().isEmpty());
        assertTrue(ctx.store().transfers().isEmpty());
        assertTrue(ctx.store().debts().isEmpty());
        assertEquals(0, ctx.store().accounts().get("SCB").balanceSatang());
        assertEquals(0, ctx.store().accounts().get("SAVING").balanceSatang());
        assertEquals(0, ctx.store().transferGraph().edgeCount());
        assertNull(ctx.notifications().heroBanner());   // rules cleared too
    }

    private Transaction lastBreadPurchase() {
        var it = ctx.store().ledger().descendingIterator();
        while (it.hasNext()) {
            Transaction txn = it.next();
            if ("Bread".equals(txn.itemName()) && txn.date().equals(MON.plusDays(7))) {
                return txn;
            }
        }
        throw new IllegalStateException("expected bread purchase not found");
    }
}
