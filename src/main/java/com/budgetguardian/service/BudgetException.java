package com.budgetguardian.service;

/**
 * Domain exception of the service layer.
 *
 * <p><b>Purpose:</b> shields controllers from JDBC details — repositories
 * throw {@code StorageException}, services wrap it (or a validation failure) in
 * this unchecked exception with a user-presentable message. The UI catches
 * only this type.</p>
 */
public class BudgetException extends RuntimeException {

    public BudgetException(String message) {
        super(message);
    }

    public BudgetException(String message, Throwable cause) {
        super(message, cause);
    }
}
