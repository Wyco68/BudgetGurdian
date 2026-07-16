/**
 * Builds middleware that validates and *replaces* `req.body` with the parsed
 * (typed, stripped) result of a zod schema. Controllers therefore only ever
 * see sanitized input. Zod errors fall through to the error handler.
 */
export function validateBody(schema) {
  return (req, _res, next) => {
    req.body = schema.parse(req.body);
    next();
  };
}
