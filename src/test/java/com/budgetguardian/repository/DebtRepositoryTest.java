package com.budgetguardian.repository;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtPayment;
import com.budgetguardian.model.DebtStatus;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trip tests for {@link DebtRepository} including partial payments. */
class DebtRepositoryTest extends RepositoryTestBase {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 6);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 12, 0);

    private Debt openDebt(LocalDate dueDate) {
        return new Debt(0, DebtDirection.PAYABLE, "Alice", 100_000, dueDate, DebtStatus.OPEN, null, NOW);
    }

    @Test
    void insertAssignsIdAndRoundTrips() throws SQLException {
        DebtRepository repository = new DebtRepository(connection);
        Debt saved = repository.insert(openDebt(DAY.plusDays(30)));
        assertTrue(saved.id() > 0);
        HashMap<Long, Debt> debts = repository.findAll();
        assertEquals(1, debts.size());
        assertEquals(saved, debts.get(saved.id()));
    }

    @Test
    void nullDueDateRoundTrips() throws SQLException {
        DebtRepository repository = new DebtRepository(connection);
        Debt saved = repository.insert(openDebt(null));
        assertNull(repository.findAll().get(saved.id()).dueDate());
    }

    @Test
    void statusFlipPersists() throws SQLException {
        DebtRepository repository = new DebtRepository(connection);
        Debt saved = repository.insert(openDebt(null));
        repository.updateStatus(saved.settled(DAY));
        Debt loaded = repository.findAll().get(saved.id());
        assertEquals(DebtStatus.SETTLED, loaded.status());
        assertEquals(DAY, loaded.settledDate());
        repository.updateStatus(loaded.reopened());
        assertEquals(DebtStatus.OPEN, repository.findAll().get(saved.id()).status());
    }

    @Test
    void paymentsRoundTripChronologically() throws SQLException {
        DebtRepository repository = new DebtRepository(connection);
        Debt debt = repository.insert(openDebt(null));
        DebtPayment second = repository.insertPayment(
                new DebtPayment(0, debt.id(), "SCB", 40_000, DAY.plusDays(5), NOW));
        DebtPayment first = repository.insertPayment(
                new DebtPayment(0, debt.id(), "SAVING", 30_000, DAY, NOW));
        DoublyLinkedList<DebtPayment> payments = repository.findAllPayments();
        assertEquals(2, payments.size());
        assertEquals(first, payments.getFirst());    // date order beats insert order
        assertEquals(second, payments.getLast());
    }

    @Test
    void deletePaymentRemovesRow() throws SQLException {
        DebtRepository repository = new DebtRepository(connection);
        Debt debt = repository.insert(openDebt(null));
        DebtPayment payment = repository.insertPayment(
                new DebtPayment(0, debt.id(), "SCB", 40_000, DAY, NOW));
        repository.deletePayment(payment.id());
        assertTrue(repository.findAllPayments().isEmpty());
        assertThrows(SQLException.class, () -> repository.deletePayment(payment.id()));
    }

    @Test
    void deletingDebtCascadesToPayments() throws SQLException {
        DebtRepository repository = new DebtRepository(connection);
        Debt debt = repository.insert(openDebt(null));
        repository.insertPayment(new DebtPayment(0, debt.id(), "SCB", 40_000, DAY, NOW));
        repository.delete(debt.id());
        assertTrue(repository.findAll().isEmpty());
        assertTrue(repository.findAllPayments().isEmpty());   // ON DELETE CASCADE
    }

    @Test
    void paymentForUnknownDebtOrAccountRejected() throws SQLException {
        DebtRepository repository = new DebtRepository(connection);
        Debt debt = repository.insert(openDebt(null));
        assertThrows(SQLException.class, () -> repository.insertPayment(
                new DebtPayment(0, 999, "SCB", 100, DAY, NOW)));
        assertThrows(SQLException.class, () -> repository.insertPayment(
                new DebtPayment(0, debt.id(), "KBANK", 100, DAY, NOW)));
    }
}
