import { Request, Response, NextFunction } from "express";
import { apiError } from "../utils/asyncHandler";

/**
 * The last thing an unhandled error passes through, and the one place that
 * decides whose fault it was. A 500 is a page for whoever is on call, so
 * anything the client got wrong has to be turned back into a 4xx here rather
 * than in each handler - every route reaches this, so nothing is missed.
 */
export function errorHandler(err: any, _req: Request, res: Response, _next: NextFunction) {
  // A model rejected the client's input (an over-long venue, say) - that is a
  // bad request, not a server fault, so it must not leave here as a 500.
  if (err?.name === "ValidationError") {
    const field = Object.keys(err.errors ?? {})[0] ?? null;
    const message = field ? err.errors[field].message : err.message;
    return res.status(400).json(apiError("VALIDATION_ERROR", message, field));
  }

  // Mongoose throws this when an id that is not a valid ObjectId reaches a
  // query. Such an id names nothing, so the honest answer is the one a
  // well-formed id for a missing document already gets - and every route that
  // takes an id documents a 404 anyway. Only ObjectId casts: calling a bad
  // number "not found" would be a lie, and that is a separate bug to chase.
  if (err?.name === "CastError" && err.kind === "ObjectId") {
    return res.status(404).json(apiError("NOT_FOUND", "Not found"));
  }

  console.error(err);
  return res.status(500).json(apiError("INTERNAL_ERROR", "Something went wrong"));
}
