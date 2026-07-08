package com.budgetguardian.repository;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.Account;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trip tests for {@link AccountRepository} against seeded accounts. */
class AccountRepositoryTest extends RepositoryTestBase {

    @Test
    void findAllReturnsFourSeededAccounts() throws SQLException {
        HashMap<String, Account> accounts = new AccountRepository(connection).findAll();
        assertEquals(4, accounts.size());
        for (String id : new String[] {"SAVING", "SCHOLARSHIP", "SCB", "TRUEMONEY"}) {
            assertTrue(accounts.containsKey(id), "missing account: " + id);
            assertEquals(0, accounts.get(id).balanceSatang());
        }
        assertEquals("Saving", accounts.get("SAVING").name());
        assertEquals("TrueMoney", accounts.get("TRUEMONEY").name());
    }

    @Test
    void updateBalancePersists() throws SQLException {
        AccountRepository repository = new AccountRepository(connection);
        repository.updateBalance("SCB", 123_456);
        assertEquals(123_456, repository.findAll().get("SCB").balanceSatang());
    }

    @Test
    void updateBalanceUnknownAccountThrows() {
        AccountRepository repository = new AccountRepository(connection);
        assertThrows(SQLException.class, () -> repository.updateBalance("KBANK", 1));
    }
}
