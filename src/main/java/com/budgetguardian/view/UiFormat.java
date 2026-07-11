package com.budgetguardian.view;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.Account;
import com.budgetguardian.model.Category;
import com.budgetguardian.service.DataStore;

import java.time.format.DateTimeFormatter;

/**
 * Small display helpers shared by views: account and category names by id,
 * and the app-wide date format. Keeps controllers free of lookup boilerplate.
 */
public final class UiFormat {

    /** App-wide date display format. */
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private UiFormat() {
    }

    /** @return the account's display name, or its id if unknown. */
    public static String accountName(DataStore store, String accountId) {
        HashMap<String, Account> accounts = store.accounts();
        Account account = accounts.get(accountId);
        return account != null ? account.name() : accountId;
    }

    /** @return the category's name, or "—" if none/unknown. */
    public static String categoryName(DataStore store, Integer categoryId) {
        if (categoryId == null) {
            return "—";
        }
        Category category = store.categories().get(categoryId);
        return category != null ? category.name() : "#" + categoryId;
    }
}
