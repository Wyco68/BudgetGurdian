package com.budgetguardian.mapper;

import com.budgetguardian.dto.TransferDto;
import com.budgetguardian.model.Transfer;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO ↔ domain conversion for transfers. */
public final class TransferMapper {

    private TransferMapper() {
    }

    public static Transfer toModel(TransferDto dto) {
        return new Transfer(
                dto.id,
                dto.fromAccount,
                dto.toAccount,
                dto.amountSatang,
                dto.reason,
                LocalDate.parse(dto.date),
                LocalDateTime.parse(dto.createdAt));
    }

    public static TransferDto toDto(Transfer transfer) {
        TransferDto dto = new TransferDto();
        dto.id = transfer.id();
        dto.fromAccount = transfer.fromAccount();
        dto.toAccount = transfer.toAccount();
        dto.amountSatang = transfer.amountSatang();
        dto.reason = transfer.reason();
        dto.date = transfer.date().toString();
        dto.createdAt = transfer.createdAt().toString();
        return dto;
    }
}
