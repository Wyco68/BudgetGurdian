// Idempotent seed: the four fixed accounts, six categories and default
// settings — the same rows the desktop's SQLite schema script seeds locally.
// Safe to run repeatedly (createMany + skipDuplicates).
import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

const ACCOUNTS = [
  { id: 'SAVING', name: 'Saving', balanceSatang: 0n, displayOrder: 1 },
  { id: 'SCHOLARSHIP', name: 'Scholarship', balanceSatang: 0n, displayOrder: 2 },
  { id: 'SCB', name: 'SCB', balanceSatang: 0n, displayOrder: 3 },
  { id: 'TRUEMONEY', name: 'TrueMoney', balanceSatang: 0n, displayOrder: 4 },
];

const CATEGORIES = [
  { id: 1, name: 'DailySpending', isDanger: false },
  { id: 2, name: 'Refill', isDanger: false },
  { id: 3, name: 'Extra', isDanger: false },
  { id: 4, name: 'Bill', isDanger: false },
  { id: 10, name: 'Alcohol', isDanger: true },
  { id: 11, name: 'Gamble', isDanger: true },
];

const SETTINGS = [
  { key: 'daily_budget', value: '18000' },
  { key: 'danger_weekly_limit', value: '20000' },
  { key: 'reminder_time', value: '20:00' },
];

async function main() {
  await prisma.account.createMany({ data: ACCOUNTS, skipDuplicates: true });
  await prisma.category.createMany({ data: CATEGORIES, skipDuplicates: true });
  await prisma.setting.createMany({ data: SETTINGS, skipDuplicates: true });
  console.log('Seed complete: 4 accounts, 6 categories, 3 settings (existing rows untouched).');
}

main()
  .catch((e) => {
    console.error(e);
    process.exitCode = 1;
  })
  .finally(() => prisma.$disconnect());
