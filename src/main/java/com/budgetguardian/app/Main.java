package com.budgetguardian.app;

import com.budgetguardian.database.DatabaseManager;
import com.budgetguardian.service.ReminderScheduler;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.view.AppShell;
import com.budgetguardian.view.DashboardView;
import com.budgetguardian.view.DebtView;
import com.budgetguardian.view.RefillsView;
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
 * <p>The database lives next to the app as {@code budget.db}.</p>
 */
public final class Main extends Application {

    private DatabaseManager database;
    private ScheduledExecutorService scheduler;

    @Override
    public void start(Stage stage) throws SQLException {
        database = new DatabaseManager(Path.of("budget.db"));
        database.open();
        ServiceContext services = new ServiceContext(database.getConnection(), LocalDate::now);

        AppShell shell = new AppShell(services);
        shell.register(new DashboardView(services, LocalDate::now));
        TransactionsView transactionsView = new TransactionsView(services, LocalDate::now);
        shell.register(transactionsView);
        shell.register(new TransfersView(services, LocalDate::now));
        shell.register(new DebtView(services, LocalDate::now));
        shell.register(new RefillsView(services, LocalDate::now));
        shell.register(new SettingsView(services));

        Scene scene = new Scene(shell.getNode(), 1180, 760);
        scene.getStylesheets().add(styleSheet());
        registerShortcuts(scene, services, transactionsView);
        stage.setScene(scene);
        stage.setTitle("Budget Guardian");
        stage.show();

        startReminderScheduler(services);
    }

    /** Global accelerators: Ctrl+N new transaction, Ctrl+Z undo. */
    private void registerShortcuts(Scene scene, ServiceContext services, TransactionsView transactionsView) {
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
                transactionsView::openAddDialog);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN),
                () -> services.undo().undo());
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
