package com.budgetguardian.service;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Account;
import com.budgetguardian.model.Bill;
import com.budgetguardian.model.Category;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtPayment;
import com.budgetguardian.model.RefillItem;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.Transfer;
import com.budgetguardian.repository.AccountRepository;
import com.budgetguardian.repository.BillRepository;
import com.budgetguardian.repository.CategoryRepository;
import com.budgetguardian.repository.DebtRepository;
import com.budgetguardian.repository.RefillRepository;
import com.budgetguardian.repository.SettingsRepository;
import com.budgetguardian.repository.TransactionRepository;
import com.budgetguardian.repository.TransferRepository;

import com.budgetguardian.repository.StorageException;

/**
 * Hydrates the {@link DataStore} from SQLite once at startup.
 *
 * <p><b>Sequence:</b> one {@code findAll} pass per table into the custom
 * structures, then derived state is rebuilt: the three total maps, the
 * recent-transactions buffer, per-debt payment lists, and the transfer
 * graph. After this the database is write-only until the next start.</p>
 *
 * <p><b>Time complexity:</b> O(total rows). <b>Space:</b> O(total rows).</p>
 */
public final class StartupLoader {

    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final TransferRepository transfers;
    private final DebtRepository debts;
    private final RefillRepository refills;
    private final SettingsRepository settings;
    private final BillRepository bills;

    public StartupLoader(AccountRepository accounts, CategoryRepository categories,
                         TransactionRepository transactions, TransferRepository transfers,
                         DebtRepository debts, RefillRepository refills,
                         SettingsRepository settings, BillRepository bills) {
        this.accounts = accounts;
        this.categories = categories;
        this.transactions = transactions;
        this.transfers = transfers;
        this.debts = debts;
        this.refills = refills;
        this.settings = settings;
        this.bills = bills;
    }

    /**
     * @return a fully hydrated store with all derived state rebuilt
     * @throws BudgetException on any storage failure
     */
    public DataStore load() {
        DataStore store = new DataStore();
        try {
            copyInto(store.accounts(), accounts.findAll().iterator());
            copyCategories(store, categories.findAll());
            copyLedger(store, transactions.findAll());
            copyTransfers(store, transfers.findAll());
            copyDebts(store, debts.findAll(), debts.findAllPayments());
            copyRefills(store, refills.findAll());
            copySettings(store, settings.findAll());
            copyBills(store, bills.findAll());
        } catch (StorageException e) {
            throw new BudgetException("Failed to load data from database", e);
        }
        store.rebuildTotals();
        store.rebuildOffsets();
        store.rebuildRecentTransactions();
        store.rebuildTransferGraph();
        return store;
    }

    private static void copyInto(HashMap<String, Account> target,
                                 Iterator<HashMap.Entry<String, Account>> source) {
        while (source.hasNext()) {
            HashMap.Entry<String, Account> entry = source.next();
            target.put(entry.key(), entry.value());
        }
    }

    private static void copyCategories(DataStore store, HashMap<Integer, Category> loaded) {
        Iterator<HashMap.Entry<Integer, Category>> it = loaded.iterator();
        while (it.hasNext()) {
            HashMap.Entry<Integer, Category> entry = it.next();
            store.categories().put(entry.key(), entry.value());
        }
    }

    private static void copyLedger(DataStore store, DoublyLinkedList<Transaction> loaded) {
        Iterator<Transaction> it = loaded.iterator();
        while (it.hasNext()) {
            store.ledger().addLast(it.next());
        }
    }

    private static void copyTransfers(DataStore store, DoublyLinkedList<Transfer> loaded) {
        Iterator<Transfer> it = loaded.iterator();
        while (it.hasNext()) {
            store.transfers().addLast(it.next());
        }
    }

    private static void copyDebts(DataStore store, HashMap<Long, Debt> loadedDebts,
                                  DoublyLinkedList<DebtPayment> loadedPayments) {
        Iterator<HashMap.Entry<Long, Debt>> debts = loadedDebts.iterator();
        while (debts.hasNext()) {
            HashMap.Entry<Long, Debt> entry = debts.next();
            store.debts().put(entry.key(), entry.value());
            store.debtPayments().put(entry.key(), new DoublyLinkedList<>());
        }
        Iterator<DebtPayment> payments = loadedPayments.iterator();
        while (payments.hasNext()) {
            DebtPayment payment = payments.next();
            store.debtPayments().get(payment.debtId()).addLast(payment);
        }
    }

    private static void copyRefills(DataStore store, HashMap<String, RefillItem> loaded) {
        Iterator<HashMap.Entry<String, RefillItem>> it = loaded.iterator();
        while (it.hasNext()) {
            HashMap.Entry<String, RefillItem> entry = it.next();
            store.refillItems().put(entry.key(), entry.value());
        }
    }

    private static void copySettings(DataStore store, HashMap<String, String> loaded) {
        Iterator<HashMap.Entry<String, String>> it = loaded.iterator();
        while (it.hasNext()) {
            HashMap.Entry<String, String> entry = it.next();
            store.settings().put(entry.key(), entry.value());
        }
    }

    private static void copyBills(DataStore store, HashMap<Long, Bill> loaded) {
        Iterator<HashMap.Entry<Long, Bill>> it = loaded.iterator();
        while (it.hasNext()) {
            HashMap.Entry<Long, Bill> entry = it.next();
            store.bills().put(entry.key(), entry.value());
        }
    }
}
