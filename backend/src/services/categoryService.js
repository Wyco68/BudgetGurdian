import { prisma } from '../prisma.js';
import { toCategoryDto } from './mappers.js';

/** @returns all categories ordered by id (read-only resource). */
export async function listCategories() {
  const rows = await prisma.category.findMany({ orderBy: { id: 'asc' } });
  return rows.map(toCategoryDto);
}
