// Row -> DTO mapping. The wire format is the contract with the desktop
// client's `dto` package: satang amounts as JSON numbers (safe well below
// 2^53 for a personal ledger), dates as "yyyy-MM-dd", timestamps as naive
// ISO date-times. Database rows never leave the service layer unmapped.
import { formatDateOnly, formatNaiveDateTime } from '../utils/dates.js';

export function toAccountDto(row) {
  return {
    id: row.id,
    name: row.name,
    balanceSatang: Number(row.balanceSatang),
    displayOrder: row.displayOrder,
  };
}

export function toCategoryDto(row) {
  return {
    id: row.id,
    name: row.name,
    danger: row.isDanger,
  };
}

export function toTransactionDto(row) {
  return {
    id: Number(row.id),
    type: row.type,
    accountId: row.accountId,
    categoryId: row.categoryId,
    itemName: row.itemName,
    amountSatang: Number(row.amountSatang),
    reason: row.reason,
    date: formatDateOnly(row.txnDate),
    createdAt: formatNaiveDateTime(row.createdAt),
  };
}

export function toTransferDto(row) {
  return {
    id: Number(row.id),
    fromAccount: row.fromAccount,
    toAccount: row.toAccount,
    amountSatang: Number(row.amountSatang),
    reason: row.reason,
    date: formatDateOnly(row.transferDate),
    createdAt: formatNaiveDateTime(row.createdAt),
  };
}

export function toDebtDto(row) {
  return {
    id: Number(row.id),
    direction: row.direction,
    person: row.person,
    amountSatang: Number(row.amountSatang),
    dueDate: row.dueDate ? formatDateOnly(row.dueDate) : null,
    status: row.status,
    settledDate: row.settledDate ? formatDateOnly(row.settledDate) : null,
    createdAt: formatNaiveDateTime(row.createdAt),
  };
}

export function toDebtPaymentDto(row) {
  return {
    id: Number(row.id),
    debtId: Number(row.debtId),
    accountId: row.accountId,
    amountSatang: Number(row.amountSatang),
    paymentDate: formatDateOnly(row.paymentDate),
    createdAt: formatNaiveDateTime(row.createdAt),
  };
}

export function toRefillItemDto(row) {
  return {
    name: row.name,
    intervalDays: row.intervalDays,
    lastPurchase: formatDateOnly(row.lastPurchase),
    purchaseCount: row.purchaseCount,
  };
}

export function toSettingDto(row) {
  return {
    key: row.key,
    value: row.value,
  };
}
