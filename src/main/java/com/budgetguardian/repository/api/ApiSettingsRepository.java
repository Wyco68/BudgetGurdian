package com.budgetguardian.repository.api;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.dto.SettingDto;
import com.budgetguardian.network.ApiException;
import com.budgetguardian.network.HttpJsonClient;
import com.budgetguardian.repository.SettingsRepository;
import com.budgetguardian.repository.StorageException;

/** REST access to {@code /settings} (key-value pairs, PUT upsert). */
public final class ApiSettingsRepository implements SettingsRepository {

    private final HttpJsonClient http;

    public ApiSettingsRepository(HttpJsonClient http) {
        this.http = http;
    }

    @Override
    public HashMap<String, String> findAll() throws StorageException {
        try {
            SettingDto[] dtos = http.get("/settings", SettingDto[].class);
            HashMap<String, String> settings = new HashMap<>();
            for (SettingDto dto : dtos) {
                settings.put(dto.key, dto.value);
            }
            return settings;
        } catch (ApiException e) {
            throw new StorageException("Failed to load settings from backend", e);
        }
    }

    @Override
    public void put(String key, String value) throws StorageException {
        try {
            SettingDto body = new SettingDto();
            body.value = value;
            http.put("/settings/" + HttpJsonClient.encodeSegment(key), body, SettingDto.class);
        } catch (ApiException e) {
            throw new StorageException("Failed to save setting " + key, e);
        }
    }
}
