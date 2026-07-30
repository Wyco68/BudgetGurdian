# Budget Guardian — Deployment Guide

Three pieces: Supabase (PostgreSQL), the Node backend, the desktop app.

## 1. Supabase

1. Create a project at [supabase.com](https://supabase.com) (free tier is fine).
2. Project Settings → Database → copy both connection strings:
   - **Transaction pooler** (port `6543`) → `DATABASE_URL` (runtime queries)
   - **Direct / session** (port `5432`) → `DIRECT_URL` (migrations only)
3. Keep the database password out of the repo — it goes into `backend/.env`
   only (`.env` is gitignored; `.env.example` holds placeholders and nothing
   else). The desktop never talks to Supabase directly — the backend is the
   sole client.
4. Create a dedicated least-privilege role for the backend and connect as
   **that**, never as `postgres`. The superuser has `BYPASSRLS`, which would
   silently defeat the policies in step 5. Run once, in the SQL editor:

   ```sql
   CREATE ROLE budget_backend LOGIN PASSWORD '<strong-random-password>';
   GRANT USAGE ON SCHEMA public TO budget_backend;
   GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO budget_backend;
   GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO budget_backend;
   ```

   Then put `budget_backend.<project-ref>` as the username in both connection
   strings.
5. Row Level Security **is** used, and is the boundary that keeps the data
   private. `prisma/migrations/20260730000000_row_level_security` enables RLS on
   every table in `public` and grants access through a single `backend_all`
   policy scoped to `budget_backend`. The `anon` and `authenticated` roles —
   whose key ships inside any Supabase client — hold table grants by Supabase
   default but have no policy, so RLS denies them every row. The migration is
   idempotent and covers tables added later, so re-run `npm run db:migrate`
   after any schema change and confirm Advisors reports no `rls_disabled`.

## 2. Backend

### Local / first run

```bash
cd backend
npm install
cp .env.example .env        # fill in DATABASE_URL, DIRECT_URL; optionally API_KEY
npm run prisma:generate
npm run db:migrate          # applies prisma/migrations against DIRECT_URL
npm run db:seed             # idempotent: 4 accounts, 11 categories, 3 settings
npm run dev                 # http://localhost:8080  (npm start for production)
```

Verify: `curl http://localhost:8080/health` → `{"status":"ok"}`.

### Hosting (Render / Railway / Fly.io / any Node host)

- Build: `npm ci && npm run prisma:generate`
- Start: `npm start`
- Release step (run once per deploy): `npm run db:migrate`
- Environment: `DATABASE_URL`, `DIRECT_URL`, `PORT` (host-provided), `API_KEY`,
  `NODE_ENV=production`, and `HOST=0.0.0.0`.
  - `API_KEY` is mandatory here — with `NODE_ENV=production` the server refuses
    to boot without it, because an empty key disables auth entirely. The
    desktop sends it as `X-API-Key`.
  - `HOST` defaults to `127.0.0.1` so a local backend is not exposed to the
    LAN. Managed hosts need `0.0.0.0` to route traffic to the container.
- Terminate TLS at the host (the desktop should call `https://...`).

### Environment separation

One Supabase project (or at least one schema/database) per environment.
Point each backend deployment's `.env` at its own database; the desktop
selects the environment purely by `api.baseUrl`.

## 3. Desktop

First launch creates an annotated config file:

| OS | Path |
|---|---|
| Windows | `%LOCALAPPDATA%\BudgetGuardian\config.properties` |
| macOS | `~/Library/Application Support/BudgetGuardian/config.properties` |
| Linux | `~/.budgetguardian/config.properties` |

Switch to cloud persistence:

```properties
storage.mode=api
api.baseUrl=https://your-backend.example.com/api/v1
api.key=<same value as backend API_KEY, or empty>
api.connectTimeoutMs=3000
api.requestTimeoutMs=10000
api.retries=2
```

Switch back anytime with `storage.mode=local` (the SQLite file is untouched
by API mode). If the backend is unreachable at startup the app shows a
dialog naming this config file instead of crashing.

### Migrating existing local data to the cloud

The schemas are 1:1. One-off copy with any SQLite→Postgres tool, or simply:
export `budget.db` tables to CSV and `\copy` them into Supabase (tables:
`account`, `category`, `txn`, `transfer`, `debt`, `debt_payment`,
`refill_item`, `setting`). Run the copy **before** first API-mode launch, or
after — the desktop always trusts the backend's state at startup.

## 4. Smoke test

```bash
# with the backend running and seeded
curl -s -H "X-API-Key: $KEY" $BASE/api/v1/accounts | jq length     # -> 4
curl -s -X POST -H "Content-Type: application/json" -H "X-API-Key: $KEY" \
  -d '{"type":"EXPENSE","accountId":"SCB","categoryId":1,"amountSatang":5000,
       "reason":"smoke","date":"2026-07-16","createdAt":"2026-07-16T12:00:00"}' \
  $BASE/api/v1/transactions | jq .id
```

Then start the desktop in API mode — the dashboard should show the smoke
transaction.
