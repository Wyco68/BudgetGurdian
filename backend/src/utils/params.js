import { ApiError } from './ApiError.js';

/** Parses a numeric id path parameter; 400 on anything non-numeric. */
export function numericId(value) {
  if (!/^\d{1,18}$/.test(value)) {
    throw ApiError.badRequest(`Invalid id: ${value}`);
  }
  return value;
}
