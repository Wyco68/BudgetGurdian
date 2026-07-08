package com.budgetguardian.model;

/**
 * Spending category. Eleven are seeded; {@code danger} marks Alcohol and
 * Gambling, whose combined Mon–Sun spending is guarded by the weekly limit.
 *
 * @param id     stable numeric key (1–11 seeded)
 * @param name   display name, unique
 * @param danger whether this category counts toward the danger-spending limit
 */
public record Category(int id, String name, boolean danger) {

    public Category {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
    }
}
