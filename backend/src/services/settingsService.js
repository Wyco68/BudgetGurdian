import { prisma } from '../prisma.js';
import { toSettingDto } from './mappers.js';

/** @returns every setting as { key, value } pairs. */
export async function listSettings() {
  const rows = await prisma.setting.findMany({ orderBy: { key: 'asc' } });
  return rows.map(toSettingDto);
}

/** Inserts or replaces one setting. */
export async function putSetting(key, value) {
  const row = await prisma.setting.upsert({
    where: { key },
    create: { key, value },
    update: { value },
  });
  return toSettingDto(row);
}
