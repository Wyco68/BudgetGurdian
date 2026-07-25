package com.budgetguardian.mapper;

import com.budgetguardian.dto.DebtDto;
import com.budgetguardian.dto.DebtPaymentDto;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtPayment;
import com.budgetguardian.model.DebtStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO ↔ domain conversion for debts and their partial payments. */
public final class DebtMapper {

    private DebtMapper() {
    }

    public static Debt toModel(DebtDto dto) {
        return new Debt(
                dto.id,
                DebtDirection.valueOf(dto.direction),
                dto.person,
                dto.amountSatang,
                dto.occurredDate != null ? LocalDate.parse(dto.occurredDate) : null,
                dto.dueDate != null ? LocalDate.parse(dto.dueDate) : null,
                DebtStatus.valueOf(dto.status),
                dto.settledDate != null ? LocalDate.parse(dto.settledDate) : null,
                LocalDateTime.parse(dto.createdAt));
    }

    public static DebtDto toDto(Debt debt) {
        DebtDto dto = new DebtDto();
        dto.id = debt.id();
        dto.direction = debt.direction().name();
        dto.person = debt.person();
        dto.amountSatang = debt.amountSatang();
        dto.occurredDate = debt.occurredDate() != null ? debt.occurredDate().toString() : null;
        dto.dueDate = debt.dueDate() != null ? debt.dueDate().toString() : null;
        dto.status = debt.status().name();
        dto.settledDate = debt.settledDate() != null ? debt.settledDate().toString() : null;
        dto.createdAt = debt.createdAt().toString();
        return dto;
    }

    public static DebtPayment toModel(DebtPaymentDto dto) {
        return new DebtPayment(
                dto.id,
                dto.debtId,
                dto.accountId,
                dto.amountSatang,
                LocalDate.parse(dto.paymentDate),
                LocalDateTime.parse(dto.createdAt));
    }

    public static DebtPaymentDto toDto(DebtPayment payment) {
        DebtPaymentDto dto = new DebtPaymentDto();
        dto.id = payment.id();
        dto.debtId = payment.debtId();
        dto.accountId = payment.accountId();
        dto.amountSatang = payment.amountSatang();
        dto.paymentDate = payment.paymentDate().toString();
        dto.createdAt = payment.createdAt().toString();
        return dto;
    }
}
