package com.budgetguardian.service;

import com.budgetguardian.model.Transfer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Business-logic tests for {@link TransferService}. */
class TransferServiceTest extends ServiceTestBase {

    private Transfer transfer(String from, String to, long satang) {
        return new Transfer(0, from, to, satang, "move", DAY, NOW);
    }

    @Test
    void transferMovesBalancesOnly() {
        transactionService.add(income("SCB", 100_000, DAY));
        transferService.add(transfer("SCB", "SAVING", 30_000));

        assertEquals(70_000, store.accounts().get("SCB").balanceSatang());
        assertEquals(30_000, store.accounts().get("SAVING").balanceSatang());
        assertEquals(0, store.dailyTotal(DAY));            // never an expense
        assertEquals(1, store.transfers().size());
        // Database agrees.
        DataStore reloaded = reload();
        assertEquals(70_000, reloaded.accounts().get("SCB").balanceSatang());
        assertEquals(30_000, reloaded.accounts().get("SAVING").balanceSatang());
    }

    @Test
    void transferAddsGraphEdge() {
        transferService.add(transfer("SCB", "SAVING", 30_000));
        transferService.add(transfer("SCB", "SAVING", 20_000));
        transferService.add(transfer("SAVING", "TRUEMONEY", 5_000));

        assertEquals(3, store.transferGraph().edgeCount());
        assertEquals(50_000, store.transferGraph().totalFlow("SCB", "SAVING"));
        assertTrue(store.transferGraph().hasPath("SCB", "TRUEMONEY"));
        assertEquals(4, store.transferGraph().vertexCount());   // all accounts are vertices
    }

    @Test
    void undoTransferRestoresBalancesAndGraph() {
        transferService.add(transfer("SCB", "SAVING", 30_000));
        assertTrue(undoService.undo());

        assertEquals(0, store.accounts().get("SCB").balanceSatang());
        assertEquals(0, store.accounts().get("SAVING").balanceSatang());
        assertTrue(store.transfers().isEmpty());
        assertEquals(0, store.transferGraph().edgeCount());     // rebuilt without the edge
        assertEquals(4, store.transferGraph().vertexCount());
        assertTrue(reload().transfers().isEmpty());
    }

    @Test
    void rejectsUnknownAccounts() {
        assertThrows(BudgetException.class,
                () -> transferService.add(transfer("SCB", "KBANK", 100)));
        assertThrows(BudgetException.class,
                () -> transferService.add(transfer("KBANK", "SCB", 100)));
    }
}
