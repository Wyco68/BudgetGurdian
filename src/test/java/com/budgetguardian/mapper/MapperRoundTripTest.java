package com.budgetguardian.mapper;

import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtPayment;
import com.budgetguardian.model.DebtStatus;
import com.budgetguardian.model.RefillItem;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.model.Transfer;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Model → DTO → model must be loss-free: the wire format carries exactly the
 * information the domain records hold, including nullable fields.
 */
class MapperRoundTripTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 16);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 16, 12, 30, 45);

    @Test
    void transactionRoundTripsAllFields() {
        Transaction txn = new Transaction(42, TransactionType.EXPENSE, "SCB", 10,
                "Beer", 15000, "Friday", DAY, NOW);
        assertEquals(txn, TransactionMapper.toModel(TransactionMapper.toDto(txn)));
    }

    @Test
    void transactionRoundTripsNullCategoryAndItem() {
        Transaction txn = new Transaction(7, TransactionType.INCOME, "SAVING", null,
                null, 500000, "Salary", DAY, NOW);
        Transaction back = TransactionMapper.toModel(TransactionMapper.toDto(txn));
        assertEquals(txn, back);
        assertNull(back.categoryId());
        assertNull(back.itemName());
    }

    @Test
    void transferRoundTrips() {
        Transfer transfer = new Transfer(3, "SCB", "TRUEMONEY", 2500, "Top up", DAY, NOW);
        assertEquals(transfer, TransferMapper.toModel(TransferMapper.toDto(transfer)));
    }

    @Test
    void debtRoundTripsWithAndWithoutDates() {
        Debt open = new Debt(1, DebtDirection.PAYABLE, "Alice", 100000, DAY.plusDays(30),
                DebtStatus.OPEN, null, NOW);
        Debt settled = new Debt(2, DebtDirection.RECEIVABLE, "Bob", 50000, null,
                DebtStatus.SETTLED, DAY, NOW);
        assertEquals(open, DebtMapper.toModel(DebtMapper.toDto(open)));
        assertEquals(settled, DebtMapper.toModel(DebtMapper.toDto(settled)));
    }

    @Test
    void debtPaymentRoundTrips() {
        DebtPayment payment = new DebtPayment(9, 1, "SCB", 25000, DAY, NOW);
        assertEquals(payment, DebtMapper.toModel(DebtMapper.toDto(payment)));
    }

    @Test
    void refillItemRoundTrips() {
        RefillItem item = new RefillItem("shampoo", 12.5, DAY, 4);
        assertEquals(item, RefillItemMapper.toModel(RefillItemMapper.toDto(item)));
    }
}
