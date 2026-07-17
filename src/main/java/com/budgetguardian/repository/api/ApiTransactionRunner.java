package com.budgetguardian.repository.api;

import com.budgetguardian.repository.StorageException;
import com.budgetguardian.repository.TransactionRunner;

/**
 * REST-mode {@link TransactionRunner}: executes the work directly.
 *
 * <p>Each REST endpoint is atomic server-side (one Prisma operation or
 * transaction), but a multi-call unit (e.g. insert expense + update balance)
 * is <em>not</em> atomic across calls. If a later call fails the desktop
 * keeps its in-memory state untouched and surfaces a retryable error — see
 * docs/ARCHITECTURE.md for the consistency discussion and the planned
 * offline sync queue that will close this gap.</p>
 */
public final class ApiTransactionRunner implements TransactionRunner {

    @Override
    public <T> T run(Work<T> work) throws StorageException {
        return work.execute();
    }
}
