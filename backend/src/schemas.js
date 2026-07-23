// Request-body validation. Every write endpoint parses its body through one
// of these schemas before any service code runs; unknown fields are stripped.
import { z } from 'zod';

/** Positive integer satang amount, within JS safe-integer range. */
const satang = z.number().int().positive().max(Number.MAX_SAFE_INTEGER);

/** Any integer satang value (balances may legitimately go negative). */
const satangBalance = z.number().int().min(Number.MIN_SAFE_INTEGER).max(Number.MAX_SAFE_INTEGER);

/** "yyyy-MM-dd" */
const dateOnly = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'expected yyyy-MM-dd');

/** Naive ISO date-time, e.g. "2026-07-16T12:00:00" (seconds optional). */
const naiveDateTime = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?$/, 'expected yyyy-MM-ddTHH:mm[:ss]');

const shortText = z.string().max(500);
const identifier = z.string().min(1).max(64);

export const balanceSchema = z.object({
  balanceSatang: satangBalance,
});

export const transactionSchema = z.object({
  type: z.enum(['EXPENSE', 'INCOME', 'WITHDRAWAL']),
  accountId: identifier,
  categoryId: z.number().int().positive().nullish(),
  itemName: shortText.nullish(),
  amountSatang: satang,
  reason: shortText,
  date: dateOnly,
  createdAt: naiveDateTime,
});

export const transferSchema = z
  .object({
    fromAccount: identifier,
    toAccount: identifier,
    amountSatang: satang,
    reason: shortText,
    date: dateOnly,
    createdAt: naiveDateTime,
  })
  .refine((t) => t.fromAccount !== t.toAccount, {
    message: 'fromAccount and toAccount must differ',
  });

export const debtSchema = z.object({
  direction: z.enum(['PAYABLE', 'RECEIVABLE']),
  person: z.string().min(1).max(200),
  amountSatang: satang,
  dueDate: dateOnly.nullish(),
  status: z.enum(['OPEN', 'SETTLED']),
  settledDate: dateOnly.nullish(),
  createdAt: naiveDateTime,
});

export const debtStatusSchema = z.object({
  status: z.enum(['OPEN', 'SETTLED']),
  settledDate: dateOnly.nullish(),
});

export const debtPaymentSchema = z.object({
  accountId: identifier,
  amountSatang: satang,
  paymentDate: dateOnly,
  createdAt: naiveDateTime,
});

export const refillItemSchema = z.object({
  intervalDays: z.number().positive().finite(),
  lastPurchase: dateOnly,
  purchaseCount: z.number().int().min(0),
});

export const billSchema = z.object({
  name: z.string().min(1).max(200),
  amountSatang: satang,
  payday: z.number().int().min(1).max(31).nullish(),
  lastPaidDate: dateOnly.nullish(),
  createdAt: naiveDateTime,
});

export const billLastPaidSchema = z.object({
  lastPaidDate: dateOnly.nullish(),
});

export const settingSchema = z.object({
  value: z.string().max(500),
});
