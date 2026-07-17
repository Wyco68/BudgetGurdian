package com.budgetguardian.mapper;

import com.budgetguardian.dto.AccountDto;
import com.budgetguardian.model.Account;

/**
 * DTO ↔ domain conversion for accounts.
 *
 * <p>Mappers are the only place wire format and business model meet: DTOs
 * never leak past the repository layer, models never touch JSON.</p>
 */
public final class AccountMapper {

    private AccountMapper() {
    }

    public static Account toModel(AccountDto dto) {
        return new Account(dto.id, dto.name, dto.balanceSatang, dto.displayOrder);
    }
}
