package com.budgetguardian.repository.api;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.dto.CategoryDto;
import com.budgetguardian.mapper.CategoryMapper;
import com.budgetguardian.model.Category;
import com.budgetguardian.network.ApiException;
import com.budgetguardian.network.HttpJsonClient;
import com.budgetguardian.repository.CategoryRepository;
import com.budgetguardian.repository.StorageException;

/** REST access to {@code /categories} (read-only, fixed set). */
public final class ApiCategoryRepository implements CategoryRepository {

    private final HttpJsonClient http;

    public ApiCategoryRepository(HttpJsonClient http) {
        this.http = http;
    }

    @Override
    public HashMap<Integer, Category> findAll() throws StorageException {
        try {
            CategoryDto[] dtos = http.get("/categories", CategoryDto[].class);
            HashMap<Integer, Category> categories = new HashMap<>();
            for (CategoryDto dto : dtos) {
                Category category = CategoryMapper.toModel(dto);
                categories.put(category.id(), category);
            }
            return categories;
        } catch (ApiException e) {
            throw new StorageException("Failed to load categories from backend", e);
        }
    }
}
