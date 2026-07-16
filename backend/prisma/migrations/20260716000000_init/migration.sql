-- CreateSchema
CREATE SCHEMA IF NOT EXISTS "public";

-- CreateEnum
CREATE TYPE "TransactionType" AS ENUM ('EXPENSE', 'INCOME', 'WITHDRAWAL');

-- CreateEnum
CREATE TYPE "DebtDirection" AS ENUM ('PAYABLE', 'RECEIVABLE');

-- CreateEnum
CREATE TYPE "DebtStatus" AS ENUM ('OPEN', 'SETTLED');

-- CreateTable
CREATE TABLE "account" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "balance_satang" BIGINT NOT NULL DEFAULT 0,
    "display_order" INTEGER NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "account_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "category" (
    "id" INTEGER NOT NULL,
    "name" TEXT NOT NULL,
    "is_danger" BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT "category_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "txn" (
    "id" BIGSERIAL NOT NULL,
    "type" "TransactionType" NOT NULL,
    "account_id" TEXT NOT NULL,
    "category_id" INTEGER,
    "item_name" TEXT,
    "amount_satang" BIGINT NOT NULL,
    "reason" TEXT NOT NULL,
    "txn_date" DATE NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "txn_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "transfer" (
    "id" BIGSERIAL NOT NULL,
    "from_account" TEXT NOT NULL,
    "to_account" TEXT NOT NULL,
    "amount_satang" BIGINT NOT NULL,
    "reason" TEXT NOT NULL,
    "transfer_date" DATE NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "transfer_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "debt" (
    "id" BIGSERIAL NOT NULL,
    "direction" "DebtDirection" NOT NULL,
    "person" TEXT NOT NULL,
    "amount_satang" BIGINT NOT NULL,
    "due_date" DATE,
    "status" "DebtStatus" NOT NULL DEFAULT 'OPEN',
    "settled_date" DATE,
    "created_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "debt_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "debt_payment" (
    "id" BIGSERIAL NOT NULL,
    "debt_id" BIGINT NOT NULL,
    "account_id" TEXT NOT NULL,
    "amount_satang" BIGINT NOT NULL,
    "payment_date" DATE NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "debt_payment_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "refill_item" (
    "name" TEXT NOT NULL,
    "interval_days" DOUBLE PRECISION NOT NULL,
    "last_purchase" DATE NOT NULL,
    "purchase_count" INTEGER NOT NULL,

    CONSTRAINT "refill_item_pkey" PRIMARY KEY ("name")
);

-- CreateTable
CREATE TABLE "setting" (
    "key" TEXT NOT NULL,
    "value" TEXT NOT NULL,

    CONSTRAINT "setting_pkey" PRIMARY KEY ("key")
);

-- CreateIndex
CREATE UNIQUE INDEX "category_name_key" ON "category"("name");

-- CreateIndex
CREATE INDEX "txn_txn_date_idx" ON "txn"("txn_date");

-- CreateIndex
CREATE INDEX "txn_category_id_idx" ON "txn"("category_id");

-- CreateIndex
CREATE INDEX "txn_item_name_idx" ON "txn"("item_name");

-- CreateIndex
CREATE INDEX "transfer_transfer_date_idx" ON "transfer"("transfer_date");

-- CreateIndex
CREATE INDEX "debt_status_idx" ON "debt"("status");

-- CreateIndex
CREATE INDEX "debt_payment_debt_id_idx" ON "debt_payment"("debt_id");

-- AddForeignKey
ALTER TABLE "txn" ADD CONSTRAINT "txn_account_id_fkey" FOREIGN KEY ("account_id") REFERENCES "account"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "txn" ADD CONSTRAINT "txn_category_id_fkey" FOREIGN KEY ("category_id") REFERENCES "category"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "transfer" ADD CONSTRAINT "transfer_from_account_fkey" FOREIGN KEY ("from_account") REFERENCES "account"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "transfer" ADD CONSTRAINT "transfer_to_account_fkey" FOREIGN KEY ("to_account") REFERENCES "account"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "debt_payment" ADD CONSTRAINT "debt_payment_debt_id_fkey" FOREIGN KEY ("debt_id") REFERENCES "debt"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "debt_payment" ADD CONSTRAINT "debt_payment_account_id_fkey" FOREIGN KEY ("account_id") REFERENCES "account"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

