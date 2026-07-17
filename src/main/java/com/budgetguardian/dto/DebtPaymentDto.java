package com.budgetguardian.dto;

/** Wire representation of one partial payment against a debt. */
public final class DebtPaymentDto {
    public long id;
    public long debtId;
    public String accountId;
    public long amountSatang;
    public String paymentDate;
    public String createdAt;
}
