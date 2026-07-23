package com.budgetguardian.service;

import com.budgetguardian.datastructures.Stack;
import com.budgetguardian.util.Money;

import java.util.Locale;

/**
 * Arithmetic-expression evaluator for the calculator feature: {@code + - * /
 * ( )} and decimals, evaluated with the classic two-stack (values/operators)
 * shunting-yard algorithm using the project's own {@code Stack} — no
 * {@code java.util} collections, per the architecture guard. Unary minus is
 * not supported (money amounts are always positive here).
 */
public final class CalculatorService {

    /**
     * @param expression e.g. {@code "120 + 45.50 * 2"}
     * @return the result in satang, rounded to 2 decimal places — the same
     *         precision {@link Money#parse} applies to manual THB entry
     * @throws BudgetException on malformed input, unbalanced parentheses or division by zero
     */
    public long evaluateToSatang(String expression) {
        double result = evaluate(expression);
        return Money.parse(String.format(Locale.ROOT, "%.2f", result));
    }

    /** @return the numeric result of {@code expression} in THB. */
    public double evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new BudgetException("Enter an expression");
        }
        Stack<Double> values = new Stack<>();
        Stack<Character> ops = new Stack<>();
        int i = 0;
        int n = expression.length();
        try {
            while (i < n) {
                char c = expression.charAt(i);
                if (Character.isWhitespace(c)) {
                    i++;
                } else if (Character.isDigit(c) || c == '.') {
                    int start = i;
                    while (i < n && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
                        i++;
                    }
                    values.push(Double.parseDouble(expression.substring(start, i)));
                } else if (c == '(') {
                    ops.push(c);
                    i++;
                } else if (c == ')') {
                    while (!ops.isEmpty() && ops.peek() != '(') {
                        apply(values, ops.pop());
                    }
                    if (ops.isEmpty()) {
                        throw new BudgetException("Unbalanced parentheses");
                    }
                    ops.pop();
                    i++;
                } else if (isOperator(c)) {
                    while (!ops.isEmpty() && ops.peek() != '(' && precedence(ops.peek()) >= precedence(c)) {
                        apply(values, ops.pop());
                    }
                    ops.push(c);
                    i++;
                } else {
                    throw new BudgetException("Unexpected character: " + c);
                }
            }
            while (!ops.isEmpty()) {
                if (ops.peek() == '(') {
                    throw new BudgetException("Unbalanced parentheses");
                }
                apply(values, ops.pop());
            }
        } catch (java.util.NoSuchElementException e) {
            throw new BudgetException("Malformed expression");
        }
        if (values.size() != 1) {
            throw new BudgetException("Malformed expression");
        }
        return values.pop();
    }

    private static void apply(Stack<Double> values, char op) {
        double b = values.pop();
        double a = values.pop();
        values.push(switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            case '*' -> a * b;
            case '/' -> {
                if (b == 0) {
                    throw new BudgetException("Division by zero");
                }
                yield a / b;
            }
            default -> throw new BudgetException("Unknown operator: " + op);
        });
    }

    private static boolean isOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }

    private static int precedence(char op) {
        return switch (op) {
            case '+', '-' -> 1;
            case '*', '/' -> 2;
            default -> 0;
        };
    }
}
