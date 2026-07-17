package com.budgetguardian.repository.api;

import com.budgetguardian.network.HttpJsonClient;
import com.budgetguardian.repository.Repositories;

/**
 * Factory assembling the full REST-backed {@link Repositories} bundle around
 * one shared {@link HttpJsonClient}. The desktop never sees a database
 * credential — only the backend's base URL (and optionally an API key).
 */
public final class ApiRepositories {

    private ApiRepositories() {
    }

    /** @return every repository plus the pass-through runner, REST-backed. */
    public static Repositories create(HttpJsonClient http) {
        return new Repositories(
                new ApiTransactionRunner(),
                new ApiAccountRepository(http),
                new ApiCategoryRepository(http),
                new ApiTransactionRepository(http),
                new ApiTransferRepository(http),
                new ApiDebtRepository(http),
                new ApiRefillRepository(http),
                new ApiSettingsRepository(http));
    }
}
