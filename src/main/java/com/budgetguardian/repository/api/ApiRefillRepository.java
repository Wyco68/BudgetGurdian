package com.budgetguardian.repository.api;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.dto.RefillItemDto;
import com.budgetguardian.mapper.RefillItemMapper;
import com.budgetguardian.model.RefillItem;
import com.budgetguardian.network.ApiException;
import com.budgetguardian.network.HttpJsonClient;
import com.budgetguardian.repository.RefillRepository;
import com.budgetguardian.repository.StorageException;

/** REST access to {@code /refill-items} (keyed by URL-encoded item name). */
public final class ApiRefillRepository implements RefillRepository {

    private final HttpJsonClient http;

    public ApiRefillRepository(HttpJsonClient http) {
        this.http = http;
    }

    @Override
    public HashMap<String, RefillItem> findAll() throws StorageException {
        try {
            RefillItemDto[] dtos = http.get("/refill-items", RefillItemDto[].class);
            HashMap<String, RefillItem> items = new HashMap<>();
            for (RefillItemDto dto : dtos) {
                RefillItem item = RefillItemMapper.toModel(dto);
                items.put(item.name(), item);
            }
            return items;
        } catch (ApiException e) {
            throw new StorageException("Failed to load refill items from backend", e);
        }
    }

    @Override
    public void upsert(RefillItem item) throws StorageException {
        try {
            http.put("/refill-items/" + HttpJsonClient.encodeSegment(item.name()),
                    RefillItemMapper.toDto(item), RefillItemDto.class);
        } catch (ApiException e) {
            throw new StorageException("Failed to save refill item " + item.name(), e);
        }
    }

    @Override
    public void delete(String name) throws StorageException {
        try {
            http.delete("/refill-items/" + HttpJsonClient.encodeSegment(name));
        } catch (ApiException e) {
            throw new StorageException("Failed to delete refill item " + name, e);
        }
    }
}
