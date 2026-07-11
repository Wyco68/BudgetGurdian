package com.budgetguardian.util;

/**
 * Formatting and parsing of satang amounts for the UI boundary.
 *
 * <p><b>Purpose:</b> the whole app stores money as {@code long} satang
 * (1 THB = 100 satang) for exact integer arithmetic. This utility converts to
 * and from the human-facing {@code "1,234.50 THB"} form used in tables,
 * cards and dialogs — the single place display conversion lives.</p>
 *
 * <p><b>Time complexity:</b> all methods O(1).</p>
 */
public final class Money {

    private Money() {
    }

    /** @return satang as grouped THB with two decimals, e.g. {@code "1,234.50 THB"}. */
    public static String format(long satang) {
        return formatPlain(satang) + " THB";
    }

    /** @return satang as grouped THB without the currency suffix, e.g. {@code "1,234.50"}. */
    public static String formatPlain(long satang) {
        boolean negative = satang < 0;
        long abs = Math.abs(satang);
        String whole = String.format("%,d", abs / 100);
        long fraction = abs % 100;
        String body = whole + "." + String.format("%02d", fraction);
        return negative ? "-" + body : body;
    }

    /**
     * Parses user input in THB (e.g. {@code "1,234.50"} or {@code "20"}) to
     * satang.
     *
     * @throws IllegalArgumentException if the text is not a valid amount
     */
    public static long parse(String thb) {
        if (thb == null || thb.isBlank()) {
            throw new IllegalArgumentException("Amount must not be blank");
        }
        String cleaned = thb.trim().replace(",", "").replace("THB", "").trim();
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(cleaned);
            java.math.BigDecimal satang = value.movePointRight(2);
            if (satang.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("Amount has more than two decimals: " + thb);
            }
            return satang.longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid amount: " + thb, e);
        }
    }
}
