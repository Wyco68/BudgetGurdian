-- Budget Guardian — SQLite schema.
-- Money is stored as INTEGER satang (1 THB = 100 satang).
-- Dates are stored as ISO-8601 TEXT (yyyy-MM-dd) and timestamps as ISO-8601 date-time.
-- The whole script is idempotent: safe to run on every startup.

CREATE TABLE IF NOT EXISTS account (
    id             TEXT    PRIMARY KEY,
    name           TEXT    NOT NULL,
    balance_satang INTEGER NOT NULL DEFAULT 0,
    display_order  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS category (
    id        INTEGER PRIMARY KEY,
    name      TEXT    NOT NULL UNIQUE,
    is_danger INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS txn (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    type          TEXT    NOT NULL CHECK (type IN ('EXPENSE', 'INCOME', 'WITHDRAWAL')),
    account_id    TEXT    NOT NULL REFERENCES account (id),
    category_id   INTEGER REFERENCES category (id),
    item_name     TEXT,
    amount_satang INTEGER NOT NULL CHECK (amount_satang > 0),
    reason        TEXT    NOT NULL,
    txn_date      TEXT    NOT NULL,
    created_at    TEXT    NOT NULL
);

-- Transfers are deliberately NOT transactions: they move money between
-- accounts without ever counting as income or expense.
CREATE TABLE IF NOT EXISTS transfer (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    from_account  TEXT    NOT NULL REFERENCES account (id),
    to_account    TEXT    NOT NULL REFERENCES account (id),
    amount_satang INTEGER NOT NULL CHECK (amount_satang > 0),
    reason        TEXT    NOT NULL,
    transfer_date TEXT    NOT NULL,
    created_at    TEXT    NOT NULL,
    CHECK (from_account <> to_account)
);

CREATE TABLE IF NOT EXISTS debt (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    direction     TEXT    NOT NULL CHECK (direction IN ('PAYABLE', 'RECEIVABLE')),
    person        TEXT    NOT NULL,
    amount_satang INTEGER NOT NULL CHECK (amount_satang > 0),
    due_date      TEXT,
    status        TEXT    NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'SETTLED')),
    settled_date  TEXT,
    created_at    TEXT    NOT NULL
);

-- Partial payments: a debt is SETTLED once the sum of its payments
-- reaches amount_satang.
CREATE TABLE IF NOT EXISTS debt_payment (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    debt_id       INTEGER NOT NULL REFERENCES debt (id) ON DELETE CASCADE,
    account_id    TEXT    NOT NULL REFERENCES account (id),
    amount_satang INTEGER NOT NULL CHECK (amount_satang > 0),
    payment_date  TEXT    NOT NULL,
    created_at    TEXT    NOT NULL
);

-- Only items the user confirmed with "Yes" are stored; declining leaves no
-- row, so the next duplicate purchase asks again.
CREATE TABLE IF NOT EXISTS refill_item (
    name           TEXT    PRIMARY KEY,
    interval_days  REAL    NOT NULL,
    last_purchase  TEXT    NOT NULL,
    purchase_count INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS setting (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- A recurring bill: optional payday (1-31) drives the "pay me" reminder.
-- Paying one logs a normal Bill-category txn and bumps last_paid_date.
CREATE TABLE IF NOT EXISTS bill (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT    NOT NULL,
    amount_satang   INTEGER NOT NULL CHECK (amount_satang > 0),
    payday          INTEGER CHECK (payday IS NULL OR (payday BETWEEN 1 AND 31)),
    last_paid_date  TEXT,
    created_at      TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_txn_date       ON txn (txn_date);
CREATE INDEX IF NOT EXISTS idx_txn_category   ON txn (category_id);
CREATE INDEX IF NOT EXISTS idx_txn_item       ON txn (item_name);
CREATE INDEX IF NOT EXISTS idx_transfer_date  ON transfer (transfer_date);
CREATE INDEX IF NOT EXISTS idx_debt_status    ON debt (status);
CREATE INDEX IF NOT EXISTS idx_payment_debt   ON debt_payment (debt_id);

INSERT OR IGNORE INTO account (id, name, balance_satang, display_order) VALUES
    ('SAVING',      'Saving',      0, 1),
    ('SCHOLARSHIP', 'Scholarship', 0, 2),
    ('SCB',         'SCB',         0, 3),
    ('TRUEMONEY',   'TrueMoney',   0, 4);

-- Six fixed categories. DailySpending is the only one counted against the
-- daily budget limit; Alcohol/Gamble are "danger" (weekly limit); Bill and
-- Alcohol both feed the separate weekly danger/bill chart.
INSERT OR IGNORE INTO category (id, name, is_danger) VALUES
    (1,  'DailySpending', 0),
    (2,  'Refill',        0),
    (3,  'Extra',         0),
    (4,  'Bill',          0),
    (10, 'Alcohol',       1),
    (11, 'Gamble',        1);

-- Idempotent migration for pre-existing databases seeded with the old
-- 11-category set: rename the survivors in place, fold the rest into Extra.
-- Each statement is a no-op once applied (name won't match again), and a
-- no-op on a fresh install (seed above already inserted the new names).
UPDATE category SET name = 'DailySpending' WHERE id = 1 AND name = 'Food';
UPDATE category SET name = 'Refill'        WHERE id = 2 AND name = 'Transport';
UPDATE category SET name = 'Bill', is_danger = 0
    WHERE id = 4 AND name = 'Bills';
UPDATE category SET name = 'Gamble'        WHERE id = 11 AND name = 'Gambling';
UPDATE txn SET category_id = 3 WHERE category_id IN (5, 6, 7, 8, 9);
UPDATE category SET name = 'Extra' WHERE id = 3 AND name = 'Shopping';
DELETE FROM category WHERE id IN (5, 6, 7, 8, 9);

INSERT OR IGNORE INTO setting (key, value) VALUES
    ('daily_budget',        '18000'),
    ('danger_weekly_limit', '20000'),
    ('reminder_time',       '20:00');
