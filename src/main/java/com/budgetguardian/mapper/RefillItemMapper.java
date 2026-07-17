package com.budgetguardian.mapper;

import com.budgetguardian.dto.RefillItemDto;
import com.budgetguardian.model.RefillItem;

import java.time.LocalDate;

/** DTO ↔ domain conversion for refillable items. */
public final class RefillItemMapper {

    private RefillItemMapper() {
    }

    public static RefillItem toModel(RefillItemDto dto) {
        return new RefillItem(
                dto.name,
                dto.intervalDays,
                LocalDate.parse(dto.lastPurchase),
                dto.purchaseCount);
    }

    public static RefillItemDto toDto(RefillItem item) {
        RefillItemDto dto = new RefillItemDto();
        dto.name = item.name();
        dto.intervalDays = item.intervalDays();
        dto.lastPurchase = item.lastPurchase().toString();
        dto.purchaseCount = item.purchaseCount();
        return dto;
    }
}
