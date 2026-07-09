package com.budgetguardian.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Observer-pattern behavior of {@link EventBus}. */
class EventBusTest {

    @Test
    void listenersReceiveOnlyTheirType() {
        EventBus bus = new EventBus();
        AtomicInteger balances = new AtomicInteger();
        AtomicInteger debts = new AtomicInteger();
        bus.subscribe(EventType.BALANCES_CHANGED, t -> balances.incrementAndGet());
        bus.subscribe(EventType.DEBTS_CHANGED, t -> debts.incrementAndGet());

        bus.publish(EventType.BALANCES_CHANGED);
        bus.publish(EventType.BALANCES_CHANGED);
        bus.publish(EventType.DEBTS_CHANGED);

        assertEquals(2, balances.get());
        assertEquals(1, debts.get());
    }

    @Test
    void multipleListenersFireInSubscriptionOrder() {
        EventBus bus = new EventBus();
        StringBuilder order = new StringBuilder();
        bus.subscribe(EventType.SETTINGS_CHANGED, t -> order.append("a"));
        bus.subscribe(EventType.SETTINGS_CHANGED, t -> order.append("b"));
        bus.publish(EventType.SETTINGS_CHANGED);
        assertEquals("ab", order.toString());
    }

    @Test
    void publishWithoutListenersIsNoOp() {
        new EventBus().publish(EventType.REFILLS_CHANGED);   // must not throw
    }

    @Test
    void rejectsNullArguments() {
        EventBus bus = new EventBus();
        assertThrows(IllegalArgumentException.class, () -> bus.subscribe(null, t -> { }));
        assertThrows(IllegalArgumentException.class, () -> bus.subscribe(EventType.DEBTS_CHANGED, null));
    }
}
