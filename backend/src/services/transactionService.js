import { prisma } from '../prisma.js';
import { ApiError } from '../utils/ApiError.js';
import { parseDateOnly, parseNaiveDateTime } from '../utils/dates.js';
import { toTransactionDto } from './mappers.js';

function toRow(dto) {
  return {
    type: dto.type,
    accountId: dto.accountId,
    categoryId: dto.categoryId ?? null,
    itemName: dto.itemName ?? null,
    amountSatang: BigInt(dto.amountSatang),
    reason: dto.reason,
    txnDate: parseDateOnly(dto.date),
    createdAt: parseNaiveDateTime(dto.createdAt),
  };
}

/** @returns the full ledger ordered by date then id (stable chronology). */
export async function listTransactions() {
  const rows = await prisma.transaction.findMany({
    orderBy: [{ txnDate: 'asc' }, { id: 'asc' }],
  });
  return rows.map(toTransactionDto);
}

/** Inserts one transaction; the database generates the id. */
export async function createTransaction(dto) {
  const row = await prisma.transaction.create({ data: toRow(dto) });
  return toTransactionDto(row);
}

/**
 * Re-creates a previously deleted transaction under its original id
 * (undo of a delete on the desktop). 409 if the id is already live.
 */
export async function restoreTransaction(id, dto) {
  const existing = await prisma.transaction.findUnique({ where: { id: BigInt(id) } });
  if (existing) {
    throw ApiError.conflict(`Transaction ${id} already exists`);
  }
  const row = await prisma.transaction.create({
    data: { id: BigInt(id), ...toRow(dto) },
  });
  return toTransactionDto(row);
}

/** Rewrites all columns of an existing transaction (edit feature). */
export async function updateTransaction(id, dto) {
  const row = await prisma.transaction.update({
    where: { id: BigInt(id) },
    data: toRow(dto),
  });
  return toTransactionDto(row);
}

/** Deletes by id (undo of an add). */
export async function deleteTransaction(id) {
  await prisma.transaction.delete({ where: { id: BigInt(id) } });
}
