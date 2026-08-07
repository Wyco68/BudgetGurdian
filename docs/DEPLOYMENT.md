# Budget Guardian — Deployment Guide

Three pieces: Neon (PostgreSQL), the Node backend, the desktop app.

## 1. Neon

1. Create a project at [neon.tech](https://neon.tech). The free plan allows 10
   projects and 0.5 GB storage — far more than this app needs.

   **Pick the region closest to where the desktop runs.** This matters more
   than it looks: `StartupLoader` issues nine sequential `findAll` calls at
   launch, so every millisecond of round-trip latency is paid nine times over.
   Measured from Thailand, the same workload took ~20 s against
   `aws-us-east-2` and ~5.4 s against `aws-ap-southeast-1` (147–386 ms per
   call warm, versus 1.3–2.4 s). The Neon MCP `create_project` tool does not
   expose a region, so create the project in the console or with the CLI:

   ```bash
   npx neonctl projects create --name budget-guardian --region-id aws-ap-southeast-1
   ```
2. Project → Connect → copy the endpoint host. You need **two** URLs, and they
   differ by *role*, not by port (Neon serves everything on `5432`):
   - `DATABASE_URL` → runtime queries, as `budget_backend`
   - `DIRECT_URL` → `prisma migrate` only, as `neondb_owner`
3. Keep the passwords out of the repo — they go into `backend/.env` only
   (`.env` and `.env.*` are gitignored; `.env.example` holds placeholders and
   nothing else). The desktop never talks to Neon directly — the backend is
   the sole client.
4. Create a dedicated least-privilege role for runtime traffic. Run once, in
   the Neon SQL editor (as `neondb_owner`):

   ```sql
   CREATE ROLE budget_backend LOGIN PASSWORD '<strong-random-password>';
   GRANT USAGE ON SCHEMA public TO budget_backend;
   ```

   Then, **after** the first `npm run db:migrate` has created the tables:

   ```sql
   GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO budget_backend;
   GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO budget_backend;
   ALTER DEFAULT PRIVILEGES IN SCHEMA public
     GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO budget_backend;
   ALTER DEFAULT PRIVILEGES IN SCHEMA public
     GRANT USAGE, SELECT ON SEQUENCES TO budget_backend;
   ```

   Order matters: `GRANT ... ON ALL TABLES` only affects tables that already
   exist. The `ALTER DEFAULT PRIVILEGES` lines cover every table a future
   migration adds, so this never has to be repeated.
5. Row Level Security **is** used, and is the boundary that keeps the data
   private. `prisma/migrations/20260730000000_row_level_security` enables RLS on
   every table in `public` and grants access through a single `backend_all`
   policy scoped to `budget_backend`. The migration is idempotent and covers
   tables added later, so re-run `npm run db:migrate` after any schema change.

   **Why two roles:** in PostgreSQL a table's owner bypasses RLS unless the
   table is set to `FORCE ROW LEVEL SECURITY`. Migrations run as the owner
   (`neondb_owner`) because only the owner can run DDL; runtime traffic runs as
   the non-owner `budget_backend`, which is what makes the policies bite.
   Pointing `DATABASE_URL` at `neondb_owner` would silently disable RLS
   entirely. Verify with:

   ```sql
   SELECT c.relname, c.relrowsecurity,
          (SELECT string_agg(p.policyname, ',') FROM pg_policies p
            WHERE p.schemaname = 'public' AND p.tablename = c.relname) AS policies
   FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE n.nspname = 'public' AND c.relkind = 'r' ORDER BY 1;
   ```

   Every row must show `relrowsecurity = true` and a `backend_all` policy.
6. Use the **direct** (non-pooled) endpoint, not the `-pooler` one. This
   backend is a single process with one Prisma pool, so the pooler adds nothing
   and only adds a failure mode. Append `connect_timeout=15` to both URLs to
   cover Neon's scale-to-zero cold start — without that margin the first
   request after an idle period fails with `P1001: Can't reach database
   server`. Do not carry over `?pgbouncer=true&connection_limit=1` from a
   Supabase setup: those are PgBouncer knobs, and `connection_limit=1` starves
   the pool here.

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

One Neon project per environment — or one Neon *branch* per environment within
a single project, which is cheaper and gives each environment a copy-on-write
clone of production data. Point each backend deployment's `.env` at its own
branch endpoint; the desktop selects the environment purely by `api.baseUrl`.

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
export `budget.db` tables to CSV and `\copy` them into Neon (tables:
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
