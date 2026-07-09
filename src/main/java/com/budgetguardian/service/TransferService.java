package com.budgetguardian.service;

import com.budgetguardian.model.Account;
import com.budgetguardian.model.Transfer;
import com.budgetguardian.repository.AccountRepository;
import com.budgetguardian.repository.TransactionRunner;
import com.budgetguardian.repository.TransferRepository;

import java.sql.SQLException;

/**
 * Business logic for transfers between accounts.
 *
 * <p><b>Rule (from requirements):</b> a transfer is never an expense — only
 * the two balances change; no category, no daily-budget impact. Each transfer
 * also becomes one directed edge in the transfer {@code Graph} used by the
 * flow visualization.</p>
 *
 * <p><b>Time complexity:</b> add O(1) memory + O(1) graph edge; undo O(n)
 * transfer-list removal + O(V+E) graph rebuild (the {@code Graph} keeps no
 * per-edge back-references — rebuilding from history is simpler and still
 * instant at personal-finance scale).</p>
 */
public final class TransferService {

    private final DataStore store;
    private final EventBus bus;
    private final TransactionRunner runner;
    private final TransferRepository transfers;
    private final AccountRepository accounts;

    public TransferService(DataStore store, EventBus bus, TransactionRunner runner,
                           TransferRepository transfers, AccountRepository accounts) {
        this.store = store;
        this.bus = bus;
        this.runner = runner;
        this.transfers = transfers;
        this.accounts = accounts;
    }

    /**
     * Records a transfer: both balances move atomically, history and graph
     * gain one entry, an undo action is pushed.
     *
     * @return the saved transfer with its generated id
     * @throws BudgetException on unknown accounts or storage failure
     */
    public Transfer add(Transfer transfer) {
        Account from = store.accounts().get(transfer.fromAccount());
        Account to = store.accounts().get(transfer.toAccount());
        if (from == null || to == null) {
            throw new BudgetException("Unknown account in transfer: "
                    + transfer.fromAccount() + " → " + transfer.toAccount());
        }
        Transfer saved;
        try {
            saved = runner.run(() -> {
                Transfer inserted = transfers.insert(transfer);
                accounts.updateBalance(from.id(), from.balanceSatang() - transfer.amountSatang());
                accounts.updateBalance(to.id(), to.balanceSatang() + transfer.amountSatang());
                return inserted;
            });
        } catch (SQLException e) {
            throw new BudgetException("Failed to save transfer", e);
        }
        store.transfers().addLast(saved);
        applyBalances(saved, +1);
        store.transferGraph().addEdge(saved.fromAccount(), saved.toAccount(),
                saved.amountSatang(), saved.reason() + " (" + saved.date() + ")");
        store.undoStack().push(new Action.AddTransfer(saved));
        publish();
        return saved;
    }

    /** Inverse of {@link #add}, called by {@code UndoService}. */
    void undoAdd(Transfer transfer) {
        Account from = store.accounts().get(transfer.fromAccount());
        Account to = store.accounts().get(transfer.toAccount());
        try {
            runner.run(() -> {
                transfers.delete(transfer.id());
                accounts.updateBalance(from.id(), from.balanceSatang() + transfer.amountSatang());
                accounts.updateBalance(to.id(), to.balanceSatang() - transfer.amountSatang());
                return null;
            });
        } catch (SQLException e) {
            throw new BudgetException("Undo failed", e);
        }
        store.transfers().remove(transfer);
        applyBalances(transfer, -1);
        store.rebuildTransferGraph();       // Graph has no edge removal by design
        publish();
    }

    private void applyBalances(Transfer transfer, int sign) {
        Account from = store.accounts().get(transfer.fromAccount());
        Account to = store.accounts().get(transfer.toAccount());
        store.accounts().put(from.id(), from.withBalance(from.balanceSatang() - sign * transfer.amountSatang()));
        store.accounts().put(to.id(), to.withBalance(to.balanceSatang() + sign * transfer.amountSatang()));
    }

    private void publish() {
        bus.publish(EventType.TRANSFERS_CHANGED);
        bus.publish(EventType.BALANCES_CHANGED);
    }
}
