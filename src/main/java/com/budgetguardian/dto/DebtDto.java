package com.budgetguardian.dto;

/** Wire representation of one debt (payable or receivable). */
public final class DebtDto {
    public long id;
    public String direction;
    public String person;
    public long amountSatang;
    public String dueDate;
    public String status;
    public String settledDate;
    public String createdAt;
}
