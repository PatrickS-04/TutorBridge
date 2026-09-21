import { Request, Response, NextFunction, RequestHandler } from "express";

// Reference: Express.js. n.d. Error handling. [Online].
//   Available at: https://expressjs.com/en/guide/error-handling/ [Accessed 21 September 2026].
export function asyncHandler(
  fn: (req: Request, res: Response, next: NextFunction) => Promise<any>
): RequestHandler {
  return (req, res, next) => {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
}

export function apiError(code: string, message: string, field: string | null = null) {
  return { error: { code, message, field } };
}
