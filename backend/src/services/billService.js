import { prisma } from '../prisma.js';
import { parseDateOnly, parseNaiveDateTime } from '../utils/dates.js';
import { toBillDto } from './mappers.js';

/** @returns every bill ordered by id. */
export async function listBills() {
  const rows = await prisma.bill.findMany({ orderBy: { id: 'asc' } });
  return rows.map(toBillDto);
}

/** Inserts one bill; the database generates the id. */
export async function createBill(dto) {
  const row = await prisma.bill.create({
    data: {
      name: dto.name,
      amountSatang: BigInt(dto.amountSatang),
      payday: dto.payday ?? null,
      lastPaidDate: dto.lastPaidDate ? parseDateOnly(dto.lastPaidDate) : null,
      createdAt: parseNaiveDateTime(dto.createdAt),
    },
  });
  return toBillDto(row);
}

/** Records a payment's effect on the bill: advances lastPaidDate. */
export async function updateBillLastPaid(id, dto) {
  const row = await prisma.bill.update({
    where: { id: BigInt(id) },
    data: { lastPaidDate: dto.lastPaidDate ? parseDateOnly(dto.lastPaidDate) : null },
  });
  return toBillDto(row);
}

/** Deletes by id. */
export async function deleteBill(id) {
  await prisma.bill.delete({ where: { id: BigInt(id) } });
}
