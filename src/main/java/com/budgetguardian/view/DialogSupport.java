package com.budgetguardian.view;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.model.Account;
import com.budgetguardian.service.DataStore;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.util.StringConverter;

import java.util.function.Function;

/** Shared field-building helpers for the transaction-family modal dialogs. */
final class DialogSupport {

    private DialogSupport() {
    }

    /** @return an account combo box populated in dashboard order. */
    static ComboBox<Account> accountCombo(DataStore store) {
        ComboBox<Account> combo = new ComboBox<>();
        DynamicArray<Account> accounts = DashboardOrder.orderedAccounts(store);
        for (int i = 0; i < accounts.size(); i++) {
            combo.getItems().add(accounts.get(i));
        }
        combo.setConverter(converter(Account::name));
        return combo;
    }

    /** @return a display-name-only converter (no parse-back; combo boxes are selection-only). */
    static <T> StringConverter<T> converter(Function<T, String> name) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : name.apply(value);
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        };
    }

    /** Applies the app stylesheet and shell style class every dialog pane needs. */
    static void applyStylesheet(Dialog<?> dialog, Class<?> owner) {
        dialog.getDialogPane().getStylesheets().add(owner.getResource("/css/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("app-shell");
    }
}
