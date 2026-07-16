/**
 * HTTP-aware error thrown by services; the error-handler middleware turns it
 * into a JSON response. Anything else that escapes becomes a 500.
 */
export class ApiError extends Error {
  /**
   * @param {number} status HTTP status code
   * @param {string} code   stable machine-readable code (e.g. "NOT_FOUND")
   * @param {string} message human-readable detail
   */
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  static notFound(what) {
    return new ApiError(404, 'NOT_FOUND', what);
  }

  static conflict(message) {
    return new ApiError(409, 'CONFLICT', message);
  }

  static badRequest(message) {
    return new ApiError(400, 'BAD_REQUEST', message);
  }
}
