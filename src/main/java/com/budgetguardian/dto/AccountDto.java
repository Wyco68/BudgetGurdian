package com.budgetguardian.dto;

/**
 * Wire representation of one account, exactly as the backend serves it.
 *
 * <p>DTOs are plain field carriers for Gson: no behavior, no business rules.
 * The {@code mapper} package converts them to/from the domain records; the
 * UI never sees a DTO.</p>
 */
public final class AccountDto {
    public String id;
    public String name;
    public long balanceSatang;
    public int displayOrder;
}
