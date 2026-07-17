package com.budgetguardian.dto;

/** Request body for {@code PUT /accounts/:id/balance} — an absolute balance. */
public final class BalanceUpdateDto {
    public long balanceSatang;

    public BalanceUpdateDto() {
    }

    public BalanceUpdateDto(long balanceSatang) {
        this.balanceSatang = balanceSatang;
    }
}
