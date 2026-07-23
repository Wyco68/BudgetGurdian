package com.budgetguardian.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Expression-evaluation logic of {@link CalculatorService}. */
class CalculatorServiceTest {

    private final CalculatorService calculator = new CalculatorService();

    @Test
    void evaluatesSimpleArithmetic() {
        assertEquals(15.0, calculator.evaluate("10 + 5"), 1e-9);
        assertEquals(5.0, calculator.evaluate("10 - 5"), 1e-9);
        assertEquals(50.0, calculator.evaluate("10 * 5"), 1e-9);
        assertEquals(2.0, calculator.evaluate("10 / 5"), 1e-9);
    }

    @Test
    void respectsOperatorPrecedence() {
        assertEquals(20.0, calculator.evaluate("10 + 5 * 2"), 1e-9);
        assertEquals(7.0, calculator.evaluate("1 + 2 * 3"), 1e-9);
    }

    @Test
    void parenthesesOverridePrecedence() {
        assertEquals(30.0, calculator.evaluate("(10 + 5) * 2"), 1e-9);
        assertEquals(45.0, calculator.evaluate("(20 - 5) * (1 + 2)"), 1e-9);
    }

    @Test
    void handlesDecimals() {
        assertEquals(166.5, calculator.evaluate("120 + 46.50"), 1e-9);
    }

    @Test
    void divisionByZeroThrows() {
        assertThrows(BudgetException.class, () -> calculator.evaluate("5 / 0"));
    }

    @Test
    void unbalancedParenthesesThrow() {
        assertThrows(BudgetException.class, () -> calculator.evaluate("(1 + 2"));
        assertThrows(BudgetException.class, () -> calculator.evaluate("1 + 2)"));
    }

    @Test
    void blankOrMalformedExpressionThrows() {
        assertThrows(BudgetException.class, () -> calculator.evaluate(""));
        assertThrows(BudgetException.class, () -> calculator.evaluate("   "));
        assertThrows(BudgetException.class, () -> calculator.evaluate("1 + "));
        assertThrows(BudgetException.class, () -> calculator.evaluate("1 2"));
    }

    @Test
    void evaluateToSatangRoundsToTwoDecimals() {
        assertEquals(16650, calculator.evaluateToSatang("120 + 46.50"));
        assertEquals(1000, calculator.evaluateToSatang("5 * 2"));
    }
}
