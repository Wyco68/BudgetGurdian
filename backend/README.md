# Budget Guardian — Backend

REST persistence tier for the Budget Guardian desktop app:
**Express 5 → zod validation → Prisma → Neon PostgreSQL**.

The desktop application is the only intended client. It downloads all data
at startup, keeps working entirely in its own in-memory data structures, and
calls back here to persist every mutation. Database credentials live only in
`.env` on this side — the desktop never sees them.

## Quick start

```bash
npm install
cp .env.example .env      # fill in DATABASE_URL, DIRECT_URL, optional API_KEY
npm run prisma:generate
npm run db:migrate        # apply prisma/migrations
npm run db:seed           # idempotent: 4 accounts, 11 categories, 3 settings
npm run dev               # http://localhost:8080
```

## Layout

```
prisma/schema.prisma      data model (BIGINT satang, BIGSERIAL ids, DATE columns)
prisma/migrations/        SQL migrations (prisma migrate deploy)
prisma/seed.js            idempotent seed data
src/server.js             entry point, graceful shutdown
src/app.js                middleware chain: helmet -> json -> auth -> routes -> errors
src/routes/               endpoint table (all under /api/v1)
src/controllers/          thin req/res glue
src/services/             Prisma calls + row->DTO mapping (rows never leak)
src/middleware/           apiKeyAuth (timing-safe), validate (zod), errorHandler
src/utils/                dates (naive-UTC contract), ApiError, param parsing
```

## Documentation

- Endpoint reference: [../docs/API.md](../docs/API.md)
- Architecture & ADRs: [../docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md)
- Neon setup & hosting: [../docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md)
