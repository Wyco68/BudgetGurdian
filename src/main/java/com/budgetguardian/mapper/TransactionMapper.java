package com.budgetguardian.mapper;

import com.budgetguardian.dto.TransactionDto;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO ↔ domain conversion for ledger transactions. */
public final class TransactionMapper {

    private TransactionMapper() {
    }

    public static Transaction toModel(TransactionDto dto) {
        return new Transaction(
                dto.id,
                TransactionType.valueOf(dto.type),
                dto.accountId,
                dto.categoryId,
                dto.itemName,
                dto.amountSatang,
                dto.reason,
                LocalDate.parse(dto.date),
                LocalDateTime.parse(dto.createdAt));
    }

    public static TransactionDto toDto(Transaction txn) {
        TransactionDto dto = new TransactionDto();
        dto.id = txn.id();
        dto.type = txn.type().name();
        dto.accountId = txn.accountId();
        dto.categoryId = txn.categoryId();
        dto.itemName = txn.itemName();
        dto.amountSatang = txn.amountSatang();
        dto.reason = txn.reason();
        dto.date = txn.date().toString();
        dto.createdAt = txn.createdAt().toString();
        return dto;
    }
}
