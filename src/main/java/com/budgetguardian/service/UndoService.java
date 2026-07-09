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

    public UndoService(DataStore store, TransactionService transactionService,
                       TransferService transferService, DebtService debtService,
                       RefillService refillService) {
        this.store = store;
        this.transactionService = transactionService;
        this.transferService = transferService;
        this.debtService = debtService;
        this.refillService = refillService;
    }

    /** @return whether an action is available to undo. O(1). */
    public boolean canUndo() {
        return !store.undoStack().isEmpty();
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
        Action action = store.undoStack().pop();
        switch (action) {
            case Action.AddTransaction(var txn) -> transactionService.undoAdd(txn);
            case Action.DeleteTransaction(var txn) -> transactionService.undoDelete(txn);
            case Action.EditTransaction(var before, var after) -> transactionService.undoEdit(before, after);
            case Action.AddTransfer(var transfer) -> transferService.undoAdd(transfer);
            case Action.AddDebt(var debt) -> debtService.undoAddDebt(debt);
            case Action.AddDebtPayment(var payment, var settled) -> debtService.undoPayment(payment, settled);
            case Action.ConfirmRefill(var item) -> refillService.undoConfirm(item);
        }
        return true;
    }
}
