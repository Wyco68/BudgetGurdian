import { timingSafeEqual } from 'node:crypto';

/**
 * Optional shared-secret authentication.
 *
 * When API_KEY is set in the environment, every request (except /health)
 * must send the same value in the "X-API-Key" header. When unset, the
 * middleware is a no-op — convenient for local development.
 */
export function apiKeyAuth(configuredKey) {
  if (!configuredKey) {
    return (_req, _res, next) => next();
  }
  const expected = Buffer.from(configuredKey);
  return (req, res, next) => {
    const provided = Buffer.from(String(req.get('x-api-key') ?? ''));
    const ok = provided.length === expected.length && timingSafeEqual(provided, expected);
    if (!ok) {
      res.status(401).json({ error: { code: 'UNAUTHORIZED', message: 'Missing or invalid API key' } });
      return;
    }
    next();
  };
}
