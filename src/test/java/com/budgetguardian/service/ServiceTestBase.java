package com.budgetguardian.service;

import com.budgetguardian.database.DatabaseManager;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.repository.AccountRepository;
import com.budgetguardian.repository.CategoryRepository;
import com.budgetguardian.repository.DebtRepository;
import com.budgetguardian.repository.RefillRepository;
import com.budgetguardian.repository.SettingsRepository;
import com.budgetguardian.repository.TransactionRepository;
import com.budgetguardian.repository.TransactionRunner;
import com.budgetguardian.repository.TransferRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import com.budgetguardian.repository.sqlite.SqliteAccountRepository;
import com.budgetguardian.repository.sqlite.SqliteCategoryRepository;
import com.budgetguardian.repository.sqlite.SqliteDebtRepository;
import com.budgetguardian.repository.sqlite.SqliteRefillRepository;
import com.budgetguardian.repository.sqlite.SqliteSettingsRepository;
import com.budgetguardian.repository.sqlite.SqliteTransactionRepository;
import com.budgetguardian.repository.sqlite.SqliteTransactionRunner;
import com.budgetguardian.repository.sqlite.SqliteTransferRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Shared fixture wiring the full stack (SQLite → repositories → services)
 * against a fresh temp database, exactly as the application composes it.
 */
abstract class ServiceTestBase {

    static final LocalDate DAY = LocalDate.of(2026, 7, 6);          // a Monday
    static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 12, 0);
    static final int FOOD = 1;
    static final int ALCOHOL = 10;
    static final int GAMBLING = 11;

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    DataStore store;
    EventBus bus;
    TransactionRepository transactionRepository;
    AccountRepository accountRepository;
    TransactionService transactionService;
    TransferService transferService;
    DebtService debtService;
    RefillService refillService;
    SettingsService settingsService;
    SearchService searchService;
    UndoService undoService;
    StartupLoader loader;

    @BeforeEach
    void wireStack() throws Exception {
        db = new DatabaseManager(tempDir.resolve("service-test.db"));
        db.open();
        var connection = db.getConnection();
        TransactionRunner runner = new SqliteTransactionRunner(connection);
        accountRepository = new SqliteAccountRepository(connection);
        CategoryRepository categoryRepository = new SqliteCategoryRepository(connection);
        transactionRepository = new SqliteTransactionRepository(connection);
        TransferRepository transferRepository = new SqliteTransferRepository(connection);
        DebtRepository debtRepository = new SqliteDebtRepository(connection);
        RefillRepository refillRepository = new SqliteRefillRepository(connection);
        SettingsRepository settingsRepository = new SqliteSettingsRepository(connection);

        loader = new StartupLoader(accountRepository, categoryRepository, transactionRepository,
                transferRepository, debtRepository, refillRepository, settingsRepository);
        store = loader.load();
        bus = new EventBus();
        transactionService = new TransactionService(store, bus, runner, transactionRepository, accountRepository);
        transferService = new TransferService(store, bus, runner, transferRepository, accountRepository);
        debtService = new DebtService(store, bus, runner, debtRepository, accountRepository);
        refillService = new RefillService(store, bus, runner, refillRepository);
        settingsService = new SettingsService(store, bus, settingsRepository);
        searchService = new SearchService(store);
        undoService = new UndoService(store, transactionService, transferService, debtService, refillService);
    }

    @AfterEach
    void closeDb() throws Exception {
        db.close();
    }

    /** Reloads a fresh store from the database — simulates an app restart. */
    DataStore reload() {
        return loader.load();
    }

    Transaction expense(String account, int category, String item, long satang, LocalDate date) {
        return new Transaction(0, TransactionType.EXPENSE, account, category, item, satang, "test", date, NOW);
    }

    Transaction income(String account, long satang, LocalDate date) {
        return new Transaction(0, TransactionType.INCOME, account, null, null, satang, "salary", date, NOW);
    }
}
