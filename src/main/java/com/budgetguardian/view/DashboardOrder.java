package com.budgetguardian.view;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Account;
import com.budgetguardian.service.DataStore;

import java.util.function.Consumer;

/**
 * Iterates the four accounts in their fixed {@code displayOrder}, since the
 * {@link com.budgetguardian.datastructures.HashMap} that stores them is
 * unordered. Used by views that render account rows/cards.
 */
public final class DashboardOrder {

    private DashboardOrder() {
    }

    /** Visits each account in ascending {@code displayOrder}. O(a²) for tiny a (=4). */
    public static void forEachAccount(DataStore store, Consumer<Account> action) {
        DynamicArray<Account> ordered = orderedAccounts(store);
        for (int i = 0; i < ordered.size(); i++) {
            action.accept(ordered.get(i));
        }
    }

    /** @return accounts sorted ascending by display order (insertion sort, a=4). */
    public static DynamicArray<Account> orderedAccounts(DataStore store) {
        DynamicArray<Account> ordered = new DynamicArray<>();
        Iterator<Account> it = store.accounts().values();
        while (it.hasNext()) {
            Account account = it.next();
            int pos = 0;
            while (pos < ordered.size() && ordered.get(pos).displayOrder() <= account.displayOrder()) {
                pos++;
            }
            ordered.insert(pos, account);
        }
        return ordered;
    }
}
