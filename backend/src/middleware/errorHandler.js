import { Prisma } from '@prisma/client';
import { ZodError } from 'zod';
import { ApiError } from '../utils/ApiError.js';

/**
 * Last middleware in the chain: translates every failure into a stable JSON
 * shape `{ error: { code, message } }` and never leaks internals.
 *
 * Express 5 forwards rejected promises from async handlers here
 * automatically — controllers contain no try/catch boilerplate.
 */
// eslint-disable-next-line no-unused-vars -- express identifies error handlers by arity
export function errorHandler(err, req, res, next) {
  if (err instanceof ApiError) {
    res.status(err.status).json({ error: { code: err.code, message: err.message } });
    return;
  }
  if (err instanceof ZodError) {
    const detail = err.issues.map((i) => `${i.path.join('.') || 'body'}: ${i.message}`).join('; ');
    res.status(400).json({ error: { code: 'VALIDATION_FAILED', message: detail } });
    return;
  }
  if (err instanceof Prisma.PrismaClientKnownRequestError) {
    if (err.code === 'P2025') {
      res.status(404).json({ error: { code: 'NOT_FOUND', message: 'Record not found' } });
      return;
    }
    if (err.code === 'P2002') {
      res.status(409).json({ error: { code: 'CONFLICT', message: 'Record already exists' } });
      return;
    }
    if (err.code === 'P2003') {
      res.status(400).json({ error: { code: 'FOREIGN_KEY', message: 'Referenced record does not exist' } });
      return;
    }
  }
  console.error(`[${new Date().toISOString()}] ${req.method} ${req.originalUrl} failed:`, err);
  res.status(500).json({ error: { code: 'INTERNAL', message: 'Internal server error' } });
}
