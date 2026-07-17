package com.budgetguardian.dto;

/**
 * Wire representation of one ledger row.
 *
 * <p>Dates travel as ISO strings ({@code yyyy-MM-dd} /
 * {@code yyyy-MM-ddTHH:mm:ss}), money as integer satang — the same
 * conventions the domain records use, so mapping is loss-free.</p>
 */
public final class TransactionDto {
    public long id;
    public String type;
    public String accountId;
    public Integer categoryId;
    public String itemName;
    public long amountSatang;
    public String reason;
    public String date;
    public String createdAt;
}
