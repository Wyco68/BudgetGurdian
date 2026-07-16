import { prisma } from '../prisma.js';
import { toAccountDto } from './mappers.js';

/** @returns all accounts in display order. */
export async function listAccounts() {
  const rows = await prisma.account.findMany({ orderBy: { displayOrder: 'asc' } });
  return rows.map(toAccountDto);
}

/**
 * Sets an account's absolute balance (the desktop always sends the computed
 * end state, never a delta — idempotent and safe to retry).
 * Throws Prisma P2025 (-> 404) if the account does not exist.
 */
export async function updateBalance(accountId, balanceSatang) {
  const row = await prisma.account.update({
    where: { id: accountId },
    data: { balanceSatang: BigInt(balanceSatang) },
  });
  return toAccountDto(row);
}
