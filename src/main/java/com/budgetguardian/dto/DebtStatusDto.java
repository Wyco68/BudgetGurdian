package com.budgetguardian.dto;

/** Request body for {@code PUT /debts/:id/status} — a status flip. */
public final class DebtStatusDto {
    public String status;
    public String settledDate;

    public DebtStatusDto() {
    }

    public DebtStatusDto(String status, String settledDate) {
        this.status = status;
        this.settledDate = settledDate;
    }
}
