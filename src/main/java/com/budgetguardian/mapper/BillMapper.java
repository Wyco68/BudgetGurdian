package com.budgetguardian.mapper;

import com.budgetguardian.dto.BillDto;
import com.budgetguardian.model.Bill;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO ↔ domain conversion for recurring bills. */
public final class BillMapper {

    private BillMapper() {
    }

    public static Bill toModel(BillDto dto) {
        return new Bill(
                dto.id,
                dto.name,
                dto.amountSatang,
                dto.payday,
                dto.lastPaidDate != null ? LocalDate.parse(dto.lastPaidDate) : null,
                LocalDateTime.parse(dto.createdAt));
    }

    public static BillDto toDto(Bill bill) {
        BillDto dto = new BillDto();
        dto.id = bill.id();
        dto.name = bill.name();
        dto.amountSatang = bill.amountSatang();
        dto.payday = bill.payday();
        dto.lastPaidDate = bill.lastPaidDate() != null ? bill.lastPaidDate().toString() : null;
        dto.createdAt = bill.createdAt().toString();
        return dto;
    }
}
