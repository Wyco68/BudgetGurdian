package com.budgetguardian.repository.api;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.dto.AccountDto;
import com.budgetguardian.dto.BalanceUpdateDto;
import com.budgetguardian.mapper.AccountMapper;
import com.budgetguardian.model.Account;
import com.budgetguardian.network.ApiException;
import com.budgetguardian.network.HttpJsonClient;
import com.budgetguardian.repository.AccountRepository;
import com.budgetguardian.repository.StorageException;

/**
 * REST access to {@code /accounts}.
 *
 * <p>Same contract as the SQLite implementation: hydrate all accounts into
 * the custom {@code HashMap} at startup, persist absolute balances on
 * mutation. Balance updates are idempotent (absolute value), so the HTTP
 * layer may retry them safely.</p>
 */
public final class ApiAccountRepository implements AccountRepository {

    private final HttpJsonClient http;

    public ApiAccountRepository(HttpJsonClient http) {
        this.http = http;
    }

    @Override
    public HashMap<String, Account> findAll() throws StorageException {
        try {
            AccountDto[] dtos = http.get("/accounts", AccountDto[].class);
            HashMap<String, Account> accounts = new HashMap<>();
            for (AccountDto dto : dtos) {
                Account account = AccountMapper.toModel(dto);
                accounts.put(account.id(), account);
            }
            return accounts;
        } catch (ApiException e) {
            throw new StorageException("Failed to load accounts from backend", e);
        }
    }

    @Override
    public void updateBalance(String accountId, long newBalanceSatang) throws StorageException {
        try {
            http.put("/accounts/" + HttpJsonClient.encodeSegment(accountId) + "/balance",
                    new BalanceUpdateDto(newBalanceSatang), AccountDto.class);
        } catch (ApiException e) {
            throw new StorageException("Failed to save balance of account " + accountId, e);
        }
    }
}
