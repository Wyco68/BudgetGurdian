package com.budgetguardian.dto;

/** Wire representation of one confirmed refillable item. */
public final class RefillItemDto {
    public String name;
    public double intervalDays;
    public String lastPurchase;
    public int purchaseCount;
}
