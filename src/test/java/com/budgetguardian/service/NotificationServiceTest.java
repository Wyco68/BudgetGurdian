package com.budgetguardian.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Priority-banner ordering and reminder queue of {@link NotificationService}. */
class NotificationServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 7, 6, 8, 0);

    private Notification of(NotificationType type, LocalDateTime at) {
        return new Notification(type, type.name(), "msg", at);
    }

    @Test
    void emptyBannerIsNull() {
        NotificationService service = new NotificationService(new EventBus());
        assertNull(service.heroBanner());
        assertEquals(0, service.activeCount());
    }

    @Test
    void highestPriorityWins() {
        NotificationService service = new NotificationService(new EventBus());
        service.raiseHero(of(NotificationType.DAILY_BUDGET_EXCEEDED, T0));   // 80
        service.raiseHero(of(NotificationType.DANGER_SPENDING, T0));         // 100
        service.raiseHero(of(NotificationType.REFILL_DUE, T0));              // 60
        assertEquals(NotificationType.DANGER_SPENDING, service.heroBanner().type());
        assertEquals(3, service.activeCount());
    }

    @Test
    void dismissRevealsNextHighest() {
        NotificationService service = new NotificationService(new EventBus());
        service.raiseHero(of(NotificationType.DANGER_SPENDING, T0));         // 100
        service.raiseHero(of(NotificationType.DEBT_OVERDUE, T0));            // 90
        service.raiseHero(of(NotificationType.DAILY_BUDGET_EXCEEDED, T0));   // 80
        assertEquals(NotificationType.DANGER_SPENDING, service.dismiss().type());
        assertEquals(NotificationType.DEBT_OVERDUE, service.heroBanner().type());
        assertEquals(NotificationType.DEBT_OVERDUE, service.dismiss().type());
        assertEquals(NotificationType.DAILY_BUDGET_EXCEEDED, service.heroBanner().type());
    }

    @Test
    void equalPriorityOlderWins() {
        NotificationService service = new NotificationService(new EventBus());
        Notification newer = of(NotificationType.REFILL_DUE, T0.plusHours(2));
        Notification older = of(NotificationType.REFILL_DUE, T0);
        service.raiseHero(newer);
        service.raiseHero(older);
        assertEquals(T0, service.heroBanner().raisedAt());       // older surfaces
    }

    @Test
    void dismissEmptyThrows() {
        NotificationService service = new NotificationService(new EventBus());
        assertThrows(NoSuchElementException.class, service::dismiss);
    }

    @Test
    void clearHeroEmptiesBanner() {
        NotificationService service = new NotificationService(new EventBus());
        service.raiseHero(of(NotificationType.DANGER_SPENDING, T0));
        service.clearHero();
        assertNull(service.heroBanner());
    }

    @Test
    void remindersAreFifo() {
        NotificationService service = new NotificationService(new EventBus());
        service.enqueueReminder(of(NotificationType.DAILY_REMINDER, T0));
        service.enqueueReminder(of(NotificationType.DAILY_REMINDER, T0.plusDays(1)));
        assertEquals(T0, service.reminders().dequeue().raisedAt());
        assertEquals(T0.plusDays(1), service.reminders().dequeue().raisedAt());
    }

    @Test
    void raiseHeroPublishesEvent() {
        EventBus bus = new EventBus();
        int[] fired = {0};
        bus.subscribe(EventType.NOTIFICATION_RAISED, t -> fired[0]++);
        NotificationService service = new NotificationService(bus);
        service.raiseHero(of(NotificationType.REFILL_DUE, T0));
        assertEquals(1, fired[0]);
    }
}
