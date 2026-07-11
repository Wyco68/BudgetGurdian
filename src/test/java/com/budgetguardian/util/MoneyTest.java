package com.budgetguardian.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Formatting and parsing round-trips for {@link Money}. */
class MoneyTest {

    @Test
    void formatsWithGroupingAndTwoDecimals() {
        assertEquals("1,234.50 THB", Money.format(123_450));
        assertEquals("0.00 THB", Money.format(0));
        assertEquals("0.05 THB", Money.format(5));
        assertEquals("-40.00 THB", Money.format(-4_000));
        assertEquals("1,000,000.00 THB", Money.format(100_000_000));
    }

    @Test
    void parsesVariousInputs() {
        assertEquals(123_450, Money.parse("1,234.50"));
        assertEquals(2_000, Money.parse("20"));
        assertEquals(2_050, Money.parse("20.5"));
        assertEquals(100, Money.parse(" 1.00 THB "));
    }

    @Test
    void roundTrips() {
        for (long satang : new long[] {0, 5, 100, 123_450, 999_999, 100_000_000}) {
            assertEquals(satang, Money.parse(Money.formatPlain(satang)));
        }
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> Money.parse(""));
        assertThrows(IllegalArgumentException.class, () -> Money.parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> Money.parse("1.234"));   // 3 decimals
        assertThrows(IllegalArgumentException.class, () -> Money.parse(null));
    }
}
