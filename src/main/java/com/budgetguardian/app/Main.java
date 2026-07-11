package com.budgetguardian.app;

import com.budgetguardian.database.DatabaseManager;
import com.budgetguardian.service.ReminderScheduler;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.view.AppShell;
import com.budgetguardian.view.DashboardView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
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

        Scene scene = new Scene(shell.getNode(), 1180, 760);
        scene.getStylesheets().add(styleSheet());
        stage.setScene(scene);
        stage.setTitle("Budget Guardian");
        stage.show();

        startReminderScheduler(services);
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
