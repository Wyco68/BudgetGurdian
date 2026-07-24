package com.budgetguardian.service;

/**
 * Owner of the undo mechanism (Ctrl+Z).
 *
 * <p><b>Design:</b> the {@code Stack<Action>} in {@link DataStore} holds one
 * immutable {@link Action} per modification, pushed by the service that made
 * it. {@link #undo()} pops and dispatches to that service's inverse
 * operation — Command pattern with an exhaustive switch over the sealed
 * {@code Action} hierarchy: forgetting a case is a compile error.</p>
 *
 * <p>Undo history is session-only (cleared on restart) and unbounded within
 * a session. There is no redo — an undone action is gone.</p>
 *
 * <p><b>Time complexity:</b> pop O(1); the dispatched inverse dominates
 * (see each service).</p>
 */
public final class UndoService {

    private final DataStore store;
    private final TransactionService transactionService;
    private final TransferService transferService;
    private final DebtService debtService;
    private final RefillService refillService;
    private final BillService billService;

    public UndoService(DataStore store, TransactionService transactionService,
                       TransferService transferService, DebtService debtService,
                       RefillService refillService, BillService billService) {
        this.store = store;
        this.transactionService = transactionService;
        this.transferService = transferService;
        this.debtService = debtService;
        this.refillService = refillService;
        this.billService = billService;
    }

    /** @return whether an action is available to undo. O(1). */
    public boolean canUndo() {
        return !store.undoStack().isEmpty();
    }

    /**
     * @return a marker of the current undo-stack depth, to pair with
     *         {@link #groupSince(int)} so a multi-step user operation collapses
     *         into a single undo unit
     */
    public int mark() {
        return store.undoStack().size();
    }

    /**
     * Collapses every action pushed since {@code mark} into one
     * {@link Action.Compound}, so a single {@link #undo()} reverses the whole
     * operation. No-op if fewer than two actions were pushed.
     */
    public void groupSince(int mark) {
        int count = store.undoStack().size() - mark;
        if (count <= 1) {
            return;
        }
        // Pop newest-first, insert at 0 so parts end up in apply order.
        com.budgetguardian.datastructures.DynamicArray<Action> parts =
                new com.budgetguardian.datastructures.DynamicArray<>();
        for (int i = 0; i < count; i++) {
            parts.insert(0, store.undoStack().pop());
        }
        store.undoStack().push(new Action.Compound(parts));
    }

    /**
     * Undoes the most recent modification.
     *
     * @return {@code true} if an action was undone, {@code false} if the
     *         stack was empty
     */
    public boolean undo() {
        if (store.undoStack().isEmpty()) {
            return false;
        }
        undoOne(store.undoStack().pop());
        return true;
    }

    /** Applies the inverse of one action (recursing into compounds). */
    private void undoOne(Action action) {
        switch (action) {
            case Action.AddTransaction(var txn) -> transactionService.undoAdd(txn);
            case Action.DeleteTransaction(var txn) -> transactionService.undoDelete(txn);
            case Action.EditTransaction(var before, var after) -> transactionService.undoEdit(before, after);
            case Action.AddTransfer(var transfer) -> transferService.undoAdd(transfer);
            case Action.AddDebt(var debt) -> debtService.undoAddDebt(debt);
            case Action.AddDebtPayment(var payment, var settled) -> debtService.undoPayment(payment, settled);
            case Action.ConfirmRefill(var item) -> refillService.undoConfirm(item);
            case Action.AddBill(var bill) -> billService.undoAddBill(bill);
            case Action.PayBill(var before, var payment) -> billService.undoPayBill(before, payment);
            case Action.Compound(var parts) -> {
                for (int i = parts.size() - 1; i >= 0; i--) {   // reverse of apply order
                    undoOne(parts.get(i));
                }
            }
        }
    }
}
