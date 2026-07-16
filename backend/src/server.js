import 'dotenv/config';
import { createApp } from './app.js';
import { prisma } from './prisma.js';

const port = Number(process.env.PORT ?? 8080);
const app = createApp({ apiKey: process.env.API_KEY });

const server = app.listen(port, () => {
  console.log(`Budget Guardian backend listening on http://localhost:${port}`);
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
