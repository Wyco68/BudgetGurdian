import { prisma } from '../prisma.js';
import { parseDateOnly, parseNaiveDateTime } from '../utils/dates.js';
import { toDebtDto, toDebtPaymentDto } from './mappers.js';

/** @returns all debts ordered by id. */
export async function listDebts() {
  const rows = await prisma.debt.findMany({ orderBy: { id: 'asc' } });
  return rows.map(toDebtDto);
}

/** @returns every payment of every debt, chronological (date then id). */
export async function listAllPayments() {
  const rows = await prisma.debtPayment.findMany({
    orderBy: [{ paymentDate: 'asc' }, { id: 'asc' }],
  });
  return rows.map(toDebtPaymentDto);
}

/** Inserts one debt; the database generates the id. */
export async function createDebt(dto) {
  const row = await prisma.debt.create({
    data: {
      direction: dto.direction,
      person: dto.person,
      amountSatang: BigInt(dto.amountSatang),
      occurredDate: dto.occurredDate ? parseDateOnly(dto.occurredDate) : null,
      dueDate: dto.dueDate ? parseDateOnly(dto.dueDate) : null,
      status: dto.status,
      settledDate: dto.settledDate ? parseDateOnly(dto.settledDate) : null,
      createdAt: parseNaiveDateTime(dto.createdAt),
    },
  });
  return toDebtDto(row);
}

/** Persists a status flip (OPEN <-> SETTLED) with its settled date. */
export async function updateDebtStatus(id, { status, settledDate }) {
  const row = await prisma.debt.update({
    where: { id: BigInt(id) },
    data: {
      status,
      settledDate: settledDate ? parseDateOnly(settledDate) : null,
    },
  });
  return toDebtDto(row);
}

/** Deletes a debt; its payments cascade at the schema level. */
export async function deleteDebt(id) {
  await prisma.debt.delete({ where: { id: BigInt(id) } });
}

/** Inserts one partial payment against a debt. */
export async function createPayment(debtId, dto) {
  const row = await prisma.debtPayment.create({
    data: {
      debtId: BigInt(debtId),
      accountId: dto.accountId,
      amountSatang: BigInt(dto.amountSatang),
      paymentDate: parseDateOnly(dto.paymentDate),
      createdAt: parseNaiveDateTime(dto.createdAt),
    },
  });
  return toDebtPaymentDto(row);
}

/** Deletes one payment (undo of a partial payment). */
export async function deletePayment(paymentId) {
  await prisma.debtPayment.delete({ where: { id: BigInt(paymentId) } });
}
