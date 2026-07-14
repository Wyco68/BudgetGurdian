package com.budgetguardian.integration;

import com.budgetguardian.database.DatabaseManager;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.service.ServiceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Danger-spending weekly window edge cases: the Mon–Sun boundary must reset
 * across a month edge, and combined spending exactly at the limit is not a
 * breach.
 */
class DangerWeekBoundaryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 9, 0);
    private static final int ALCOHOL = 10;
    private static final int GAMBLING = 11;

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private ServiceContext ctx;

    @BeforeEach
    void open() throws SQLException {
        db = new DatabaseManager(tempDir.resolve("danger.db"));
        db.open();
        ctx = new ServiceContext(db.getConnection(), LocalDate::now);
    }

    @AfterEach
    void close() throws SQLException {
        db.close();
    }

    private void danger(int category, long satang, LocalDate date) {
        ctx.transactions().add(new Transaction(0, TransactionType.EXPENSE, "SCB",
                category, null, satang, "x", date, NOW));
    }

    @Test
    void weekResetsAcrossMonthEnd() {
        // Sunday 2026-08-30 and Monday 2026-08-31 are in different Mon–Sun weeks.
        LocalDate sunday = LocalDate.of(2026, 8, 30);
        LocalDate monday = LocalDate.of(2026, 8, 31);
        danger(ALCOHOL, 18_000, sunday);
        danger(GAMBLING, 9_000, monday);

        assertEquals(18_000, ctx.store().dangerWeekTotal(sunday));
        assertEquals(9_000, ctx.store().dangerWeekTotal(monday));   // new week
    }

    @Test
    void combinedTotalAccumulatesWithinSameWeek() {
        LocalDate monday = LocalDate.of(2026, 7, 6);
        danger(ALCOHOL, 12_000, monday);
        danger(GAMBLING, 8_000, monday.plusDays(6));   // Sunday, same week
        assertEquals(20_000, ctx.store().dangerWeekTotal(monday));   // exactly the limit
        // Rule breach is strictly greater-than; this is handled in RuleEngineTest.
    }
}
