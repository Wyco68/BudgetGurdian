package com.budgetguardian.service;

import com.budgetguardian.repository.Repositories;
import com.budgetguardian.repository.sqlite.SqliteRepositories;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Composition root for the whole service layer.
 *
 * <p><b>Purpose:</b> takes a ready {@link Repositories} bundle (SQLite or
 * REST — the services never know which), hydrates the {@link DataStore}
 * via {@link StartupLoader}, and wires all services, the {@link EventBus},
 * the {@link NotificationService} and the {@link RuleEngine} into one ready
 * object graph. {@code Main} constructs one of these after choosing the
 * storage mode; controllers pull the services they need from it. Keeping the
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
    private final BillService billService;
    private final SettingsService settingsService;
    private final SearchService searchService;
    private final ReportService reportService;
    private final UndoService undoService;
    private final NotificationService notificationService;
    private final RuleEngine ruleEngine;
    private final CalculatorService calculatorService;

    /** Convenience constructor for the SQLite mode (and existing tests). */
    public ServiceContext(Connection connection, Supplier<LocalDate> today) {
        this(SqliteRepositories.create(connection), today);
    }

    public ServiceContext(Repositories repos, Supplier<LocalDate> today) {
        StartupLoader loader = new StartupLoader(repos.accounts(), repos.categories(),
                repos.transactions(), repos.transfers(), repos.debts(), repos.refills(),
                repos.settings(), repos.bills());
        this.store = loader.load();
        this.bus = new EventBus();
        this.transactionService = new TransactionService(store, bus, repos.runner(), repos.transactions(), repos.accounts());
        this.transferService = new TransferService(store, bus, repos.runner(), repos.transfers(), repos.accounts());
        this.debtService = new DebtService(store, bus, repos.runner(), repos.debts(), repos.accounts());
        this.refillService = new RefillService(store, bus, repos.runner(), repos.refills());
        this.billService = new BillService(store, bus, repos.runner(), repos.bills(), transactionService);
        this.settingsService = new SettingsService(store, bus, repos.settings());
        this.searchService = new SearchService(store);
        this.reportService = new ReportService(store);
        this.undoService = new UndoService(store, transactionService, transferService, debtService,
                refillService, billService);
        this.notificationService = new NotificationService(bus);
        this.ruleEngine = new RuleEngine(store, bus, notificationService, settingsService, refillService, today);
        ruleEngine.evaluate();
        this.calculatorService = new CalculatorService();
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

    public BillService bills() {
        return billService;
    }

    public SettingsService settings() {
        return settingsService;
    }

    public SearchService search() {
        return searchService;
    }

    public ReportService reports() {
        return reportService;
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

    public CalculatorService calculator() {
        return calculatorService;
    }
}
