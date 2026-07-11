package com.budgetguardian.service;

import com.budgetguardian.repository.AccountRepository;
import com.budgetguardian.repository.CategoryRepository;
import com.budgetguardian.repository.DebtRepository;
import com.budgetguardian.repository.RefillRepository;
import com.budgetguardian.repository.SettingsRepository;
import com.budgetguardian.repository.TransactionRepository;
import com.budgetguardian.repository.TransactionRunner;
import com.budgetguardian.repository.TransferRepository;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Composition root for the whole service layer.
 *
 * <p><b>Purpose:</b> builds every repository, hydrates the {@link DataStore}
 * via {@link StartupLoader}, and wires all services, the {@link EventBus},
 * the {@link NotificationService} and the {@link RuleEngine} into one ready
 * object graph. {@code Main} constructs one of these after opening the
 * database; controllers pull the services they need from it. Keeping the
 * wiring here (not in {@code Main}) makes the full stack constructible in
 * tests too.</p>
 *
 * <p>The {@code today} supplier lets tests fix the date; production passes
 * {@code LocalDate::now}.</p>
 */
public final class ServiceContext {

    private final DataStore store;
    private final EventBus bus;
    private final TransactionService transactionService;
    private final TransferService transferService;
    private final DebtService debtService;
    private final RefillService refillService;
    private final SettingsService settingsService;
    private final SearchService searchService;
    private final UndoService undoService;
    private final NotificationService notificationService;
    private final RuleEngine ruleEngine;

    public ServiceContext(Connection connection, Supplier<LocalDate> today) {
        TransactionRunner runner = new TransactionRunner(connection);
        AccountRepository accountRepository = new AccountRepository(connection);
        CategoryRepository categoryRepository = new CategoryRepository(connection);
        TransactionRepository transactionRepository = new TransactionRepository(connection);
        TransferRepository transferRepository = new TransferRepository(connection);
        DebtRepository debtRepository = new DebtRepository(connection);
        RefillRepository refillRepository = new RefillRepository(connection);
        SettingsRepository settingsRepository = new SettingsRepository(connection);

        StartupLoader loader = new StartupLoader(accountRepository, categoryRepository,
                transactionRepository, transferRepository, debtRepository, refillRepository,
                settingsRepository);
        this.store = loader.load();
        this.bus = new EventBus();
        this.transactionService = new TransactionService(store, bus, runner, transactionRepository, accountRepository);
        this.transferService = new TransferService(store, bus, runner, transferRepository, accountRepository);
        this.debtService = new DebtService(store, bus, runner, debtRepository, accountRepository);
        this.refillService = new RefillService(store, bus, runner, refillRepository);
        this.settingsService = new SettingsService(store, bus, settingsRepository);
        this.searchService = new SearchService(store);
        this.undoService = new UndoService(store, transactionService, transferService, debtService, refillService);
        this.notificationService = new NotificationService(bus);
        this.ruleEngine = new RuleEngine(store, bus, notificationService, settingsService, refillService, today);
        ruleEngine.evaluate();
    }

    public DataStore store() {
        return store;
    }

    public EventBus bus() {
        return bus;
    }

    public TransactionService transactions() {
        return transactionService;
    }

    public TransferService transfers() {
        return transferService;
    }

    public DebtService debts() {
        return debtService;
    }

    public RefillService refills() {
        return refillService;
    }

    public SettingsService settings() {
        return settingsService;
    }

    public SearchService search() {
        return searchService;
    }

    public UndoService undo() {
        return undoService;
    }

    public NotificationService notifications() {
        return notificationService;
    }

    public RuleEngine ruleEngine() {
        return ruleEngine;
    }
}
