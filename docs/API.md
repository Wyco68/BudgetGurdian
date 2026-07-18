# Budget Guardian — REST API Reference

Base URL: `http://<host>:<port>/api/v1`
Content type: `application/json` both ways.

## Authentication

Optional. When `API_KEY` is set in the backend's `.env`, every `/api/v1`
request must send:

```
X-API-Key: <the same value>
```

`401 UNAUTHORIZED` otherwise. `/health` never requires a key.

## Conventions

| Concept | Format | Example |
|---|---|---|
| Money | integer **satang** (1 THB = 100) | `15000` = ฿150.00 |
| Date | `yyyy-MM-dd` | `"2026-07-16"` |
| Timestamp | naive ISO, treated as UTC | `"2026-07-16T12:00:00"` |
| Ids | numeric (`transactions`, `transfers`, `debts`, payments), string (`accounts`, `refill-items`, `settings`) | `77`, `"SCB"` |

### Error shape (every non-2xx)

```json
{ "error": { "code": "NOT_FOUND", "message": "Record not found" } }
```

| Status | Code | Meaning |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Body failed zod validation (message lists the fields) |
| 400 | `BAD_REQUEST` / `FOREIGN_KEY` | Malformed id / referenced record missing |
| 401 | `UNAUTHORIZED` | Missing/invalid `X-API-Key` |
| 404 | `NOT_FOUND` | No such record or endpoint |
| 409 | `CONFLICT` | Duplicate (e.g. restore of a live id) |
| 500 | `INTERNAL` | Unexpected failure (details logged server-side only) |

---

## Health

| Method | Path | Response |
|---|---|---|
| GET | `/health` | `{"status":"ok"}` — no auth, for probes |

## Accounts (fixed set — only balances change)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/accounts` | – | `AccountDto[]` in display order |
| PUT | `/accounts/:id/balance` | `{"balanceSatang": -5000}` (absolute, may be negative) | updated `AccountDto` |

```json
{ "id": "SCB", "name": "SCB", "balanceSatang": 120050, "displayOrder": 3 }
```

## Categories (read-only)

| Method | Path | Response |
|---|---|---|
| GET | `/categories` | `CategoryDto[]` ordered by id |

```json
{ "id": 10, "name": "Alcohol", "danger": true }
```

## Transactions (the ledger)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/transactions` | – | `TransactionDto[]` ordered by date, id |
| POST | `/transactions` | `TransactionDto` (no id) | `201` + DTO with generated id |
| POST | `/transactions/:id/restore` | `TransactionDto` | `201` + DTO — re-creates under the original id (undo of a delete); `409` if live |
| PUT | `/transactions/:id` | `TransactionDto` | updated DTO; `404` if missing |
| DELETE | `/transactions/:id` | – | `204` |

```json
{
  "id": 77, "type": "EXPENSE", "accountId": "SCB", "categoryId": 1,
  "itemName": "Rice", "amountSatang": 5000, "reason": "lunch",
  "date": "2026-07-16", "createdAt": "2026-07-16T12:00:00"
}
```

`type` ∈ `EXPENSE | INCOME | WITHDRAWAL`. `categoryId`/`itemName` nullable.

## Transfers

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/transfers` | – | `TransferDto[]` ordered by date, id |
| POST | `/transfers` | `TransferDto` (no id; `fromAccount ≠ toAccount`) | `201` + DTO with id |
| DELETE | `/transfers/:id` | – | `204` |

```json
{
  "id": 3, "fromAccount": "SCB", "toAccount": "TRUEMONEY",
  "amountSatang": 2500, "reason": "Top up",
  "date": "2026-07-16", "createdAt": "2026-07-16T09:30:00"
}
```

## Debts & payments

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/debts` | – | `DebtDto[]` |
| POST | `/debts` | `DebtDto` (no id) | `201` + DTO with id |
| PUT | `/debts/:id/status` | `{"status":"SETTLED","settledDate":"2026-07-16"}` | updated `DebtDto` |
| DELETE | `/debts/:id` | – | `204` (payments cascade) |
| GET | `/debt-payments` | – | `DebtPaymentDto[]` of **all** debts, chronological |
| POST | `/debts/:id/payments` | `DebtPaymentDto` (no id/debtId) | `201` + DTO with id |
| DELETE | `/debt-payments/:paymentId` | – | `204` |

```json
{
  "id": 1, "direction": "PAYABLE", "person": "Alice", "amountSatang": 100000,
  "dueDate": "2026-08-15", "status": "OPEN", "settledDate": null,
  "createdAt": "2026-07-16T10:00:00"
}
```

```json
{
  "id": 9, "debtId": 1, "accountId": "SCB", "amountSatang": 25000,
  "paymentDate": "2026-07-16", "createdAt": "2026-07-16T10:05:00"
}
```

## Refill items (keyed by URL-encoded name)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/refill-items` | – | `RefillItemDto[]` |
| PUT | `/refill-items/:name` | `{"intervalDays":14.0,"lastPurchase":"2026-07-16","purchaseCount":2}` | upserted DTO |
| DELETE | `/refill-items/:name` | – | `204` |

## Settings (key-value)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/settings` | – | `SettingDto[]` |
| PUT | `/settings/:key` | `{"value":"20000"}` | upserted `{"key":"...","value":"..."}` |

---

## Idempotency & retries (client contract)

The desktop's HTTP client retries **GET and PUT only** (both idempotent —
PUT bodies carry absolute state, e.g. an absolute balance). POST and DELETE
are never retried automatically; a failed mutation is surfaced to the user,
whose local memory is left untouched so the action can simply be repeated.
