import express from 'express';
import helmet from 'helmet';
import { apiKeyAuth } from './middleware/apiKeyAuth.js';
import { errorHandler } from './middleware/errorHandler.js';
import { apiRouter } from './routes/index.js';

/**
 * Builds the Express app. Split from server.js so tests can construct the
 * app without binding a port.
 *
 * Chain: helmet -> JSON body limit -> /health (unauthenticated) ->
 * API-key gate -> /api/v1 resources -> 404 -> error handler.
 */
export function createApp({ apiKey } = {}) {
  const app = express();
  app.disable('x-powered-by');
  app.use(helmet());
  app.use(express.json({ limit: '1mb' }));

  app.get('/health', (_req, res) => {
    res.json({ status: 'ok' });
  });

  app.use('/api/v1', apiKeyAuth(apiKey), apiRouter);

  app.use((_req, res) => {
    res.status(404).json({ error: { code: 'NOT_FOUND', message: 'No such endpoint' } });
  });
  app.use(errorHandler);
  return app;
}
