-- Records the day a debt was incurred, so a receivable's repayments can offset
-- that day's spending total. Nullable for existing rows.
ALTER TABLE "debt" ADD COLUMN IF NOT EXISTS "occurred_date" DATE;
