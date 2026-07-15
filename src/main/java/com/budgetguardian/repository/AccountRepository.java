package com.budgetguardian.repository;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.Account;

/**
 * Persistence contract for accounts.
 *
 * <p>Accounts are fixed (seeded by the storage layer); implementations only
 * hydrate them at startup and persist balance changes. Implementations:
 * {@code repository.sqlite} (local file) and {@code repository.api}
 * (REST backend).</p>
 */
public interface AccountRepository {

    /** @return all accounts keyed by id, hydrated in one pass. */
    HashMap<String, Account> findAll() throws StorageException;

    /**
     * Persists a new absolute balance for one account.
     *
     * @throws StorageException if the account does not exist or the store fails
     */
    void updateBalance(String accountId, long newBalanceSatang) throws StorageException;
}
