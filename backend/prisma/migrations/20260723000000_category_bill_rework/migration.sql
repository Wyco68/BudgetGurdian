-- Collapse the eleven legacy categories into the six-category model, and
-- add the new Bill table. Idempotent: every UPDATE is guarded by the old
-- name so re-running this file (or applying it to an already-migrated
-- database) is a no-op.

-- CreateTable
CREATE TABLE IF NOT EXISTS "bill" (
    "id" BIGSERIAL NOT NULL,
    "name" TEXT NOT NULL,
    "amount_satang" BIGINT NOT NULL,
    "payday" INTEGER,
    "last_paid_date" DATE,
    "created_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "bill_pkey" PRIMARY KEY ("id")
);

-- Category rework: rename survivors, fold the rest into Extra.
UPDATE "category" SET "name" = 'DailySpending' WHERE "id" = 1 AND "name" = 'Food';
UPDATE "category" SET "name" = 'Refill'        WHERE "id" = 2 AND "name" = 'Transport';
UPDATE "category" SET "name" = 'Bill', "is_danger" = false
    WHERE "id" = 4 AND "name" = 'Bills';
UPDATE "category" SET "name" = 'Gamble'        WHERE "id" = 11 AND "name" = 'Gambling';
UPDATE "txn" SET "category_id" = 3 WHERE "category_id" IN (5, 6, 7, 8, 9);
UPDATE "category" SET "name" = 'Extra' WHERE "id" = 3 AND "name" = 'Shopping';
DELETE FROM "category" WHERE "id" IN (5, 6, 7, 8, 9);
