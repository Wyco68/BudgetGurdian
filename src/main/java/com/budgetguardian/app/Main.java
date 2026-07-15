package com.budgetguardian.app;

import com.budgetguardian.database.DatabaseManager;
import com.budgetguardian.service.ReminderScheduler;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.view.AppShell;
import com.budgetguardian.view.CalendarView;
import com.budgetguardian.view.DashboardView;
import com.budgetguardian.view.DebtView;
import com.budgetguardian.view.GraphView;
import com.budgetguardian.view.RefillsView;
import com.budgetguardian.view.ReportsView;
import com.budgetguardian.view.SearchView;
import com.budgetguardian.view.SettingsView;
import com.budgetguardian.view.TransactionsView;
import com.budgetguardian.view.TransfersView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
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
 * <p><b>Startup:</b> open the SQLite database (creating/seeding on first run),
 * build the {@link ServiceContext} (which hydrates the in-memory store and
 * wires every service, the rule engine and notifications), construct the UI
 * shell with its views, and start the daemon {@link ReminderScheduler} that
 * marshals its work onto the JavaFX thread via {@link Platform#runLater}.</p>
 *
 * <p>The database lives at a stable per-user location (see
 * {@link #databaseFile()}) so every launch — from Maven or the packaged
 * executable — shares the same data.</p>
 */
public final class Main extends Application {

    private DatabaseManager database;
    private ScheduledExecutorService scheduler;

    @Override
    public void start(Stage stage) throws SQLException {
        database = new DatabaseManager(databaseFile());
        database.open();
        ServiceContext services = new ServiceContext(database.getConnection(), LocalDate::now);

        AppShell shell = new AppShell(services);
        shell.register(new DashboardView(services, LocalDate::now));
        shell.register(new CalendarView(services, LocalDate::now));
        TransactionsView transactionsView = new TransactionsView(services, LocalDate::now);
        shell.register(transactionsView);
        shell.register(new TransfersView(services, LocalDate::now));
        shell.register(new DebtView(services, LocalDate::now));
        shell.register(new RefillsView(services, LocalDate::now));
        shell.register(new ReportsView(services, LocalDate::now));
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
    }

    /**
     * Resolves the database to a single stable per-user location, independent
     * of the working directory the app was launched from.
     *
     * <p>A relative {@code "budget.db"} resolves against the current working
     * directory, which differs between launch methods (running from Maven vs.
     * double-clicking the packaged executable) — that would silently create a
     * separate database per launch context. Anchoring it under the OS
     * per-user data directory guarantees every launch opens the same file.</p>
     *
     * <ul>
     *   <li>Windows: {@code %LOCALAPPDATA%\BudgetGuardian\budget.db}</li>
     *   <li>macOS: {@code ~/Library/Application Support/BudgetGuardian/budget.db}</li>
     *   <li>Linux/other: {@code ~/.budgetguardian/budget.db}</li>
     * </ul>
     *
     * <p>A legacy {@code budget.db} in the working directory is migrated to the
     * new location on first run so existing data is not lost.</p>
     */
    private static Path databaseFile() {
        Path dir = userDataDir();
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot create data directory: " + dir, e);
        }
        Path target = dir.resolve("budget.db");
        migrateLegacyDatabase(target);
        return target;
    }

    private static Path userDataDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path base = (localAppData != null && !localAppData.isBlank())
                    ? Path.of(localAppData)
                    : Path.of(home, "AppData", "Local");
            return base.resolve("BudgetGuardian");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "BudgetGuardian");
        }
        return Path.of(home, ".budgetguardian");
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
        ReminderScheduler reminder = new ReminderScheduler(
                services.store(), services.notifications(), services.settings(),
                LocalDateTime::now, Platform::runLater);
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
    }

    private static String styleSheet() {
        return Main.class.getResource("/css/styles.css").toExternalForm();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
