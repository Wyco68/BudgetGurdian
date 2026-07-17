package com.budgetguardian.mapper;

import com.budgetguardian.dto.CategoryDto;
import com.budgetguardian.model.Category;

/** DTO ↔ domain conversion for categories. */
public final class CategoryMapper {

    private CategoryMapper() {
    }

    public static Category toModel(CategoryDto dto) {
        return new Category(dto.id, dto.name, dto.danger);
    }
}
