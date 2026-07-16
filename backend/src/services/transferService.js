import { prisma } from '../prisma.js';
import { parseDateOnly, parseNaiveDateTime } from '../utils/dates.js';
import { toTransferDto } from './mappers.js';

/** @returns full transfer history ordered by date then id. */
export async function listTransfers() {
  const rows = await prisma.transfer.findMany({
    orderBy: [{ transferDate: 'asc' }, { id: 'asc' }],
  });
  return rows.map(toTransferDto);
}

/** Inserts one transfer; the database generates the id. */
export async function createTransfer(dto) {
  const row = await prisma.transfer.create({
    data: {
      fromAccount: dto.fromAccount,
      toAccount: dto.toAccount,
      amountSatang: BigInt(dto.amountSatang),
      reason: dto.reason,
      transferDate: parseDateOnly(dto.date),
      createdAt: parseNaiveDateTime(dto.createdAt),
    },
  });
  return toTransferDto(row);
}

/** Deletes by id (undo of a transfer). */
export async function deleteTransfer(id) {
  await prisma.transfer.delete({ where: { id: BigInt(id) } });
}
