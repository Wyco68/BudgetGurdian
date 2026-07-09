package com.budgetguardian.service;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Account;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtPayment;
import com.budgetguardian.model.DebtStatus;
import com.budgetguardian.repository.AccountRepository;
import com.budgetguardian.repository.DebtRepository;
import com.budgetguardian.repository.TransactionRunner;

import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Business logic for debts with partial payments.
 *
 * <p><b>Rules:</b> paying a {@code PAYABLE} debt decreases the chosen
 * account; receiving on a {@code RECEIVABLE} debt increases it. A debt
 * settles automatically when its payments sum to the full amount; undoing the
 * settling payment reopens it. A payment may not exceed the remaining
 * amount.</p>
 *
 * <p><b>Structures:</b> debts in {@code HashMap<Long, Debt>} (O(1) lookup);
 * each debt's payments in a chronological {@code DoublyLinkedList}.</p>
 *
 * <p><b>Time complexity:</b> pay O(p) (sums p payments of one debt);
 * add/undo O(1) beyond that.</p>
 */
public final class DebtService {

    private final DataStore store;
    private final EventBus bus;
    private final TransactionRunner runner;
    private final DebtRepository debts;
    private final AccountRepository accounts;

    public DebtService(DataStore store, EventBus bus, TransactionRunner runner,
                       DebtRepository debts, AccountRepository accounts) {
        this.store = store;
        this.bus = bus;
        this.runner = runner;
        this.debts = debts;
        this.accounts = accounts;
    }

    /**
     * Creates a new open debt.
     *
     * @return the saved debt with its generated id
     */
    public Debt add(Debt debt) {
        Debt saved;
        try {
            saved = runner.run(() -> debts.insert(debt));
        } catch (SQLException e) {
            throw new BudgetException("Failed to save debt", e);
        }
        store.debts().put(saved.id(), saved);
        store.debtPayments().put(saved.id(), new DoublyLinkedList<>());
        store.undoStack().push(new Action.AddDebt(saved));
        bus.publish(EventType.DEBTS_CHANGED);
        return saved;
    }

    /**
     * Records a partial payment (or receipt) on an open debt. The account
     * balance moves per debt direction; the debt settles automatically when
     * payments reach the full amount.
     *
     * @return the saved payment with its generated id
     * @throws BudgetException on unknown debt/account, settled debt, or
     *                         payment exceeding the remaining amount
     */
    public DebtPayment pay(long debtId, String accountId, long amountSatang, LocalDate paymentDate) {
        Debt debt = store.debts().get(debtId);
        if (debt == null) {
            throw new BudgetException("No such debt: " + debtId);
        }
        if (debt.status() == DebtStatus.SETTLED) {
            throw new BudgetException("Debt already settled: " + debt.person());
        }
        Account account = store.accounts().get(accountId);
        if (account == null) {
            throw new BudgetException("Unknown account: " + accountId);
        }
        long remaining = remaining(debtId);
        if (amountSatang > remaining) {
            throw new BudgetException("Payment " + amountSatang + " exceeds remaining " + remaining);
        }
        DebtPayment payment = new DebtPayment(0, debtId, accountId, amountSatang,
                paymentDate, java.time.LocalDateTime.now());
        boolean settles = amountSatang == remaining;
        Debt settled = settles ? debt.settled(paymentDate) : null;

        DebtPayment saved;
        try {
            saved = runner.run(() -> {
                DebtPayment inserted = debts.insertPayment(payment);
                accounts.updateBalance(accountId, account.balanceSatang() + balanceEffect(debt, amountSatang));
                if (settles) {
                    debts.updateStatus(settled);
                }
                return inserted;
            });
        } catch (SQLException e) {
            throw new BudgetException("Failed to save payment", e);
        }
        store.debtPayments().get(debtId).addLast(saved);
        store.accounts().put(accountId, account.withBalance(
                account.balanceSatang() + balanceEffect(debt, amountSatang)));
        if (settles) {
            store.debts().put(debtId, settled);
        }
        store.undoStack().push(new Action.AddDebtPayment(saved, settles));
        bus.publish(EventType.DEBTS_CHANGED);
        bus.publish(EventType.BALANCES_CHANGED);
        return saved;
    }

    /** @return satang still unpaid on a debt. O(p). */
    public long remaining(long debtId) {
        Debt debt = store.debts().get(debtId);
        if (debt == null) {
            throw new BudgetException("No such debt: " + debtId);
        }
        return debt.amountSatang() - paidTotal(debtId);
    }

    /** @return satang paid so far on a debt. O(p). */
    public long paidTotal(long debtId) {
        DoublyLinkedList<DebtPayment> payments = store.debtPayments().get(debtId);
        long total = 0;
        if (payments != null) {
            Iterator<DebtPayment> it = payments.iterator();
            while (it.hasNext()) {
                total += it.next().amountSatang();
            }
        }
        return total;
    }

    // ---- inverse operations used by UndoService --------------------------------

    /** Inverse of {@link #add}: allowed only while the debt has no payments. */
    void undoAddDebt(Debt debt) {
        DoublyLinkedList<DebtPayment> payments = store.debtPayments().get(debt.id());
        if (payments != null && !payments.isEmpty()) {
            throw new BudgetException("Cannot undo debt creation after payments exist");
        }
        try {
            runner.run(() -> {
                debts.delete(debt.id());
                return null;
            });
        } catch (SQLException e) {
            throw new BudgetException("Undo failed", e);
        }
        store.debts().remove(debt.id());
        store.debtPayments().remove(debt.id());
        bus.publish(EventType.DEBTS_CHANGED);
    }

    /**
     * Inverse of {@link #pay}: removes the payment, restores the balance and
     * reopens the debt if this payment had settled it.
     */
    void undoPayment(DebtPayment payment, boolean settledByThisPayment) {
        Debt debt = store.debts().get(payment.debtId());
        Account account = store.accounts().get(payment.accountId());
        Debt reopened = settledByThisPayment ? debt.reopened() : null;
        try {
            runner.run(() -> {
                debts.deletePayment(payment.id());
                accounts.updateBalance(payment.accountId(),
                        account.balanceSatang() - balanceEffect(debt, payment.amountSatang()));
                if (settledByThisPayment) {
                    debts.updateStatus(reopened);
                }
                return null;
            });
        } catch (SQLException e) {
            throw new BudgetException("Undo failed", e);
        }
        store.debtPayments().get(payment.debtId()).remove(payment);
        store.accounts().put(account.id(), account.withBalance(
                account.balanceSatang() - balanceEffect(debt, payment.amountSatang())));
        if (settledByThisPayment) {
            store.debts().put(reopened.id(), reopened);
        }
        bus.publish(EventType.DEBTS_CHANGED);
        bus.publish(EventType.BALANCES_CHANGED);
    }

    /** Paying a payable drains the account; receiving on a receivable fills it. */
    private static long balanceEffect(Debt debt, long amountSatang) {
        return debt.direction() == DebtDirection.PAYABLE ? -amountSatang : amountSatang;
    }
}
