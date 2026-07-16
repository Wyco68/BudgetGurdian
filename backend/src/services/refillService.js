import { prisma } from '../prisma.js';
import { parseDateOnly } from '../utils/dates.js';
import { toRefillItemDto } from './mappers.js';

/** @returns all refillable items. */
export async function listRefillItems() {
  const rows = await prisma.refillItem.findMany({ orderBy: { name: 'asc' } });
  return rows.map(toRefillItemDto);
}

/** Inserts or replaces one item (name is the primary key). */
export async function upsertRefillItem(name, dto) {
  const data = {
    intervalDays: dto.intervalDays,
    lastPurchase: parseDateOnly(dto.lastPurchase),
    purchaseCount: dto.purchaseCount,
  };
  const row = await prisma.refillItem.upsert({
    where: { name },
    create: { name, ...data },
    update: data,
  });
  return toRefillItemDto(row);
}

/** Deletes one item (undo of a "Yes" confirmation). */
export async function deleteRefillItem(name) {
  await prisma.refillItem.delete({ where: { name } });
}
