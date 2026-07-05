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

INSERT OR IGNORE INTO category (id, name, is_danger) VALUES
    (1,  'Food',          0),
    (2,  'Transport',     0),
    (3,  'Shopping',      0),
    (4,  'Bills',         0),
    (5,  'Health',        0),
    (6,  'Entertainment', 0),
    (7,  'Education',     0),
    (8,  'Investment',    0),
    (9,  'Gift',          0),
    (10, 'Alcohol',       1),
    (11, 'Gambling',      1);

INSERT OR IGNORE INTO setting (key, value) VALUES
    ('daily_budget',        '18000'),
    ('danger_weekly_limit', '20000'),
    ('reminder_time',       '20:00');
