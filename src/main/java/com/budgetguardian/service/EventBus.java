package com.budgetguardian.service;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.datastructures.Iterator;

/**
 * Observer-pattern event bus decoupling services from the UI.
 *
 * <p><b>Purpose:</b> services publish an {@link EventType} after every state
 * change; controllers subscribe and refresh their views. Neither side knows
 * the other's type.</p>
 *
 * <p><b>Design:</b> listener lists live in a
 * {@code HashMap<EventType, DynamicArray<Listener>>} — both custom
 * structures. Publish is synchronous, on the caller's thread (the JavaFX
 * application thread for all mutations, by the app's threading model).</p>
 *
 * <p><b>Time complexity:</b> subscribe O(1) amortized; publish O(k) for k
 * listeners of that type. <b>Space complexity:</b> O(total listeners).</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * bus.subscribe(EventType.BALANCES_CHANGED, type -> refreshBalances());
 * bus.publish(EventType.BALANCES_CHANGED);
 * }</pre>
 */
public final class EventBus {

    /** Receiver of published events. */
    @FunctionalInterface
    public interface Listener {
        void onEvent(EventType type);
    }

    private final HashMap<EventType, DynamicArray<Listener>> listeners = new HashMap<>();

    /** Registers a listener for one event type. O(1) amortized. */
    public void subscribe(EventType type, Listener listener) {
        if (type == null || listener == null) {
            throw new IllegalArgumentException("Type and listener must not be null");
        }
        DynamicArray<Listener> list = listeners.get(type);
        if (list == null) {
            list = new DynamicArray<>();
            listeners.put(type, list);
        }
        list.append(listener);
    }

    /** Notifies all listeners of {@code type}, in subscription order. O(k). */
    public void publish(EventType type) {
        DynamicArray<Listener> list = listeners.get(type);
        if (list == null) {
            return;
        }
        Iterator<Listener> it = list.iterator();
        while (it.hasNext()) {
            it.next().onEvent(type);
        }
    }
}
