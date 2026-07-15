package com.budgetguardian.repository;

/**
 * Executes multi-repository work as one logical unit.
 *
 * <p><b>Purpose:</b> service-layer operations often touch several stores
 * (insert expense + update balance; insert transfer + update two balances;
 * insert payment + flip debt status). The SQLite implementation wraps the
 * work in a real SQL transaction; the REST implementation executes each call
 * immediately — every endpoint is atomic server-side, and the desktop keeps
 * memory untouched if any call fails (see architecture docs for the
 * cross-call consistency note and the planned offline sync queue).</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * long id = runner.run(() -> {
 *     long txnId = transactionRepository.insert(txn);
 *     accountRepository.updateBalance(accountId, newBalance);
 *     return txnId;
 * });
 * }</pre>
 */
public interface TransactionRunner {

    /**
     * Unit of storage work executed as one logical operation.
     *
     * @param <T> result type
     */
    @FunctionalInterface
    interface Work<T> {
        T execute() throws StorageException;
    }

    /**
     * Runs {@code work}; implementations decide the atomicity guarantee.
     *
     * @throws StorageException from the work or the commit
     */
    <T> T run(Work<T> work) throws StorageException;
}
