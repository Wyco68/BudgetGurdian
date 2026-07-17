package com.budgetguardian.dto;

/** Wire representation of one transfer between accounts. */
public final class TransferDto {
    public long id;
    public String fromAccount;
    public String toAccount;
    public long amountSatang;
    public String reason;
    public String date;
    public String createdAt;
}
