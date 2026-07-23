package com.budgetguardian.service;

import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Bill;
import com.budgetguardian.model.Category;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.repository.BillRepository;
import com.budgetguardian.repository.TransactionRunner;

import com.budgetguardian.repository.StorageException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Business logic for recurring bills.
 *
 * <p><b>Paying a bill</b> delegates the money movement to
 * {@link TransactionService#add}: a normal Bill-category expense, so it
 * shows up in the ledger, totals and reports like any other spending. That
 * call pushes its own {@link Action.AddTransaction} undo entry; this class
 * immediately pops it and pushes one combined {@link Action.PayBill} instead,
 * so paying a bill is a single undoable step, not two. There is no separate
 * payment ledger — {@link Bill#lastPaidDate()} is the only payment history
 * kept per bill.</p>
 */
public final class BillService {

    /** Category expenses log bill payments under. */
    public static final String BILL_CATEGORY_NAME = "Bill";

    private final DataStore store;
    private final EventBus bus;
    private final TransactionRunner runner;
    private final BillRepository bills;
    private final TransactionService transactions;

    public BillService(DataStore store, EventBus bus, TransactionRunner runner,
                       BillRepository bills, TransactionService transactions) {
        this.store = store;
        this.bus = bus;
        this.runner = runner;
        this.bills = bills;
        this.transactions = transactions;
    }

    /**
     * Creates a new bill.
     *
     * @return the saved bill with its generated id
     */
    public Bill add(Bill bill) {
        Bill saved;
        try {
            saved = runner.run(() -> bills.insert(bill));
        } catch (StorageException e) {
            throw new BudgetException("Failed to save bill", e);
        }
        store.bills().put(saved.id(), saved);
        store.undoStack().push(new Action.AddBill(saved));
        bus.publish(EventType.BILLS_CHANGED);
        return saved;
    }

    /**
     * Records a payment: logs a Bill-category expense of {@code amountSatang}
     * on {@code accountId} and advances the bill's {@link Bill#lastPaidDate()}.
     *
     * @throws BudgetException on unknown bill or a missing Bill category
     */
    public Bill pay(long billId, String accountId, long amountSatang, LocalDate paidDate, String reason) {
        Bill bill = store.bills().get(billId);
        if (bill == null) {
            throw new BudgetException("No such bill: " + billId);
        }
        Transaction payment = new Transaction(0, TransactionType.EXPENSE, accountId, billCategoryId(),
                bill.name(), amountSatang, reason == null ? "" : reason.trim(), paidDate,
                LocalDateTime.now());
        Transaction saved = transactions.add(payment);
        store.undoStack().pop();   // discard the AddTransaction; PayBill below replaces it

        Bill updated = bill.withLastPaidDate(paidDate);
        try {
            runner.run(() -> {
                bills.updateLastPaid(updated);
                return null;
            });
        } catch (StorageException e) {
            throw new BudgetException("Failed to update bill " + billId, e);
        }
        store.bills().put(billId, updated);
        store.undoStack().push(new Action.PayBill(bill, saved));
        bus.publish(EventType.BILLS_CHANGED);
        return updated;
    }

    /** Deletes a bill. Past payments already logged in the ledger are untouched. */
    public void delete(long billId) {
        try {
            runner.run(() -> {
                bills.delete(billId);
                return null;
            });
        } catch (StorageException e) {
            throw new BudgetException("Failed to delete bill " + billId, e);
        }
        store.bills().remove(billId);
        bus.publish(EventType.BILLS_CHANGED);
    }

    /** @return the id of the fixed "Bill" category. O(c) over the (tiny) category set. */
    private int billCategoryId() {
        Iterator<Category> it = store.categories().values();
        while (it.hasNext()) {
            Category category = it.next();
            if (category.name().equals(BILL_CATEGORY_NAME)) {
                return category.id();
            }
        }
        throw new BudgetException("Bill category not seeded");
    }

    // ---- inverse operations used by UndoService --------------------------------

    /** Inverse of {@link #add}: delete without pushing a new action. */
    void undoAddBill(Bill bill) {
        try {
            runner.run(() -> {
                bills.delete(bill.id());
                return null;
            });
        } catch (StorageException e) {
            throw new BudgetException("Undo failed", e);
        }
        store.bills().remove(bill.id());
        bus.publish(EventType.BILLS_CHANGED);
    }

    /** Inverse of {@link #pay}: undoes the logged payment and restores the previous {@code lastPaidDate}. */
    void undoPayBill(Bill before, Transaction payment) {
        transactions.undoAdd(payment);
        try {
            runner.run(() -> {
                bills.updateLastPaid(before);
                return null;
            });
        } catch (StorageException e) {
            throw new BudgetException("Undo failed", e);
        }
        store.bills().put(before.id(), before);
        bus.publish(EventType.BILLS_CHANGED);
    }
}
