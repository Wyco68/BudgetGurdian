package com.budgetguardian.model;

/**
 * One of the four fixed accounts: Saving, Scholarship, SCB, TrueMoney.
 *
 * <p>Immutable value object — balance changes produce a new instance via
 * {@link #withBalance(long)}, which keeps undo snapshots trivially safe.
 * Stored in a {@code HashMap<String, Account>} keyed by {@link #id()}.</p>
 *
 * @param id            stable key, e.g. {@code "SAVING"}
 * @param name          display name, e.g. {@code "Saving"}
 * @param balanceSatang current balance in satang (may be negative — overdraft is the user's business)
 * @param displayOrder  UI ordering, 1-based
 */
public record Account(String id, String name, long balanceSatang, int displayOrder) {

    public Account {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Account id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Account name must not be blank");
        }
    }

    /** @return copy of this account with a new balance. O(1). */
    public Account withBalance(long newBalanceSatang) {
        return new Account(id, name, newBalanceSatang, displayOrder);
    }
}
