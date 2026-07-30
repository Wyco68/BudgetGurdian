import 'dotenv/config';
import { createApp } from './app.js';
import { prisma } from './prisma.js';

const port = Number(process.env.PORT ?? 8080);

// Fail closed. Without API_KEY the auth middleware is a deliberate no-op, which
// is what you want on a developer machine and never what you want in
// production — an empty key there would publish the whole ledger.
if (process.env.NODE_ENV === 'production' && !process.env.API_KEY) {
  throw new Error('API_KEY must be set when NODE_ENV=production');
}

// Bind loopback by default: the only client is the desktop app on the same
// machine, so there is no reason to answer the rest of the LAN. Set HOST
// (e.g. 0.0.0.0) to expose it deliberately — only ever with API_KEY set.
const host = process.env.HOST ?? '127.0.0.1';
const app = createApp({ apiKey: process.env.API_KEY });

const server = app.listen(port, host, () => {
  console.log(`Budget Guardian backend listening on http://${host}:${port}`);
});

// Graceful shutdown: stop accepting connections, then release the DB pool.
async function shutdown(signal) {
  console.log(`${signal} received, shutting down`);
  server.close(async () => {
    await prisma.$disconnect();
    process.exit(0);
  });
}

process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));
