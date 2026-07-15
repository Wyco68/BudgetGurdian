package com.budgetguardian.repository;

/**
 * Storage-neutral checked exception thrown by every repository interface.
 *
 * <p><b>Purpose:</b> the service layer must not know which backing store is
 * in use. SQLite implementations wrap {@link java.sql.SQLException}, REST
 * implementations wrap I/O and HTTP failures — services catch only this type
 * and translate it into a user-facing {@code BudgetException}.</p>
 */
public class StorageException extends Exception {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
