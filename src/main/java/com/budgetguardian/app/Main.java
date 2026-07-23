package com.budgetguardian.app;

import com.budgetguardian.database.DatabaseManager;
import com.budgetguardian.network.HttpJsonClient;
import com.budgetguardian.repository.Repositories;
import com.budgetguardian.repository.api.ApiRepositories;
import com.budgetguardian.repository.sqlite.SqliteRepositories;
import com.budgetguardian.service.BudgetException;
import com.budgetguardian.service.ReminderScheduler;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.view.AppShell;
import com.budgetguardian.view.BillsView;
import com.budgetguardian.view.CalculatorView;
import com.budgetguardian.view.CalendarView;
import com.budgetguardian.view.DangerView;
import com.budgetguardian.view.DashboardView;
import com.budgetguardian.view.DebtView;
import com.budgetguardian.view.GraphView;
import com.budgetguardian.view.RefillsView;
import com.budgetguardian.view.ReportsView;
import com.budgetguardian.view.SearchView;
import com.budgetguardian.view.SettingsView;
import com.budgetguardian.view.TransactionsView;
import com.budgetguardian.view.TransfersView;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.util.Duration;
import javafx.scene.control.Alert;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Application entry point and composition root.
 *
 * <p><b>Startup:</b> read {@link AppConfig}, build the {@link Repositories}
 * bundle for the configured storage mode — a local SQLite file (default) or
 * the REST backend — then construct the {@link ServiceContext} (which
 * downloads/hydrates all data into the in-memory store and wires every
 * service, the rule engine and notifications), build the UI shell, and start
 * the daemon {@link ReminderScheduler} that marshals its work onto the
 * JavaFX thread via {@link Platform#runLater} and additionally raises the
 * daily reminder as an OS-level notification via {@link OsNotifier}.</p>
 *
 * <p>In API mode the desktop never opens a database connection and holds no
 * database credentials; every read and write goes through the repository
 * layer's HTTP client. If a loopback backend isn't already running,
 * {@link BackendLauncher} starts it and stops it again on exit; a backend
 * that's remote, already running, or fails to come up in time falls through
 * to a friendly dialog instead of a stack trace.</p>
 */
public final class Main extends Application {

    private DatabaseManager database;
    private ScheduledExecutorService scheduler;
    private final BackendLauncher backendLauncher = new BackendLauncher();
    private OsNotifier osNotifier;

    @Override
    public void start(Stage stage) {
        AppConfig config = AppConfig.load();
        backendLauncher.ensureRunning(config);
        ServiceContext services;
        try {
            services = new ServiceContext(buildRepositories(config), LocalDate::now);
        } catch (BudgetException | SQLException e) {
            showStartupError(config, e);
            Platform.exit();
            return;
        }

        AppShell shell = new AppShell(services);
        shell.register(new DashboardView(services, LocalDate::now));
        shell.register(new CalendarView(services, LocalDate::now));
        TransactionsView transactionsView = new TransactionsView(services, LocalDate::now);
        shell.register(transactionsView);
        shell.register(new TransfersView(services, LocalDate::now));
        shell.register(new DebtView(services, LocalDate::now));
        shell.register(new BillsView(services, LocalDate::now));
        shell.register(new RefillsView(services, LocalDate::now));
        shell.register(new ReportsView(services, LocalDate::now));
        shell.register(new DangerView(services, LocalDate::now));
        shell.register(new CalculatorView(services, LocalDate::now));
        SearchView searchView = new SearchView(services);
        shell.register(searchView);
        shell.register(new GraphView(services, LocalDate::now));
        shell.register(new SettingsView(services));

        Scene scene = new Scene(shell.getNode(), 1180, 760);
        scene.getStylesheets().add(styleSheet());
        registerShortcuts(scene, services, shell, transactionsView, searchView);
        stage.setScene(scene);
        stage.setTitle("Budget Guardian");
        stage.show();

        startReminderScheduler(services);
        maybeSelfExitForSmokeTest();
    }

    /**
     * CI boot-smoke hook: when {@code -Dbudgetguardian.smokeTest=true} is set,
     * the app has reached a fully wired, shown window without throwing. Print a
     * marker the pipeline greps for and exit cleanly after a short delay (long
     * enough for one render pass), so the packaged application can be verified
     * to launch headlessly without a human closing the window. No effect on a
     * normal run.
     */
    private void maybeSelfExitForSmokeTest() {
        if (!Boolean.getBoolean("budgetguardian.smokeTest")) {
            return;
        }
        System.out.println("BUDGET_GUARDIAN_SMOKE_OK");
        System.out.flush();
        PauseTransition delay = new PauseTransition(Duration.seconds(2));
        delay.setOnFinished(event -> Platform.exit());
        delay.play();
    }

    /**
     * Builds the repository bundle for the configured storage mode. Local
     * mode opens (and owns) the SQLite database; API mode builds one shared
     * HTTP client — no database connection ever exists on the desktop.
     */
    private Repositories buildRepositories(AppConfig config) throws SQLException {
        if (config.mode() == AppConfig.StorageMode.API) {
            HttpJsonClient http = new HttpJsonClient(
                    config.apiBaseUrl(),
                    config.connectTimeout(),
                    config.requestTimeout(),
                    config.retries(),
                    config.apiKey());
            return ApiRepositories.create(http);
        }
        database = new DatabaseManager(databaseFile());
        database.open();
        return SqliteRepositories.create(database.getConnection());
    }

    /** Friendly startup failure: says what failed and how to fix it. */
    private static void showStartupError(AppConfig config, Exception e) {
        String hint = config.mode() == AppConfig.StorageMode.API
                ? "The backend at " + config.apiBaseUrl() + " could not be reached.\n"
                        + "Start the backend (or switch storage.mode back to 'local' in\n"
                        + AppConfig.userDataDir().resolve("config.properties") + ") and try again."
                : "The local database could not be opened.";
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Budget Guardian — cannot start");
        alert.setHeaderText("Failed to load your data");
        alert.setContentText(hint + "\n\nDetails: " + rootMessage(e));
        alert.showAndWait();
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    /**
     * Resolves the database to a single stable per-user location, independent
     * of the working directory the app was launched from (running from Maven
     * vs. double-clicking the packaged executable must share one file).
     * A legacy {@code budget.db} in the working directory is migrated once.
     */
    private static Path databaseFile() {
        Path target = AppConfig.userDataDir().resolve("budget.db");
        migrateLegacyDatabase(target);
        return target;
    }

    /** Copies a working-directory {@code budget.db} to the stable path once, if present. */
    private static void migrateLegacyDatabase(Path target) {
        if (java.nio.file.Files.exists(target)) {
            return;
        }
        Path legacy = Path.of("budget.db");
        if (java.nio.file.Files.exists(legacy)) {
            try {
                java.nio.file.Files.copy(legacy, target);
            } catch (java.io.IOException e) {
                // Non-fatal: a fresh database will be created at the target path.
                System.err.println("Could not migrate legacy database: " + e.getMessage());
            }
        }
    }

    /** Global accelerators: Ctrl+N new transaction, Ctrl+Z undo, Ctrl+F search. */
    private void registerShortcuts(Scene scene, ServiceContext services, AppShell shell,
                                   TransactionsView transactionsView, SearchView searchView) {
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
                transactionsView::openAddDialog);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN),
                () -> services.undo().undo());
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                () -> {
                    shell.show(searchView);
                    searchView.focusQuery();
                });
    }

    private void startReminderScheduler(ServiceContext services) {
        osNotifier = new OsNotifier();
        ReminderScheduler reminder = new ReminderScheduler(
                services.store(), services.notifications(), services.settings(),
                LocalDateTime::now, Platform::runLater,
                notification -> osNotifier.notify(notification.title(), notification.message()));
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "reminder-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(reminder::tick, 0, 60, TimeUnit.SECONDS);
    }

    @Override
    public void stop() throws SQLException {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (database != null) {
            database.close();
        }
        if (osNotifier != null) {
            osNotifier.close();
        }
        backendLauncher.stop();
    }

    private static String styleSheet() {
        return Main.class.getResource("/css/styles.css").toExternalForm();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
