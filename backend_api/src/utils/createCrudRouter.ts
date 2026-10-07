import { Router } from "express";
import { Model } from "mongoose";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { paginate } from "../utils/paginate";
import { authenticate, requireRole } from "../middleware/auth";
import { UserRole } from "../models";

interface CrudOptions {
  /**
   * Whether anything still points at this record. A record that is in use is
   * not deleted: a tutor's subject or a request's area would be left dangling.
   */
  inUse?: (id: string) => Promise<boolean>;
}

// Reference: Express.js. n.d. Routing. [Online].
//   Available at: https://expressjs.com/en/guide/routing/ [Accessed 21 September 2026].
/**
 * Reads are open to any signed-in user. Writes are PLATFORM_ADMIN only: these
 * collections have no institutionId, so every institution shares them and an
 * institution admin editing one would be editing it for all the others.
 */
export function createCrudRouter<T>(model: Model<T>, options: CrudOptions = {}) {
  const router = Router();
  router.use(authenticate);

  router.get(
    "/",
    asyncHandler(async (req, res) => {
      const result = await paginate(model, {}, req);
      res.status(200).json(result);
    })
  );

  router.get(
    "/:id",
    asyncHandler(async (req, res) => {
      const doc = await model.findById(req.params.id);
      if (!doc) return res.status(404).json(apiError("NOT_FOUND", "Not found"));
      res.status(200).json(doc);
    })
  );

  router.post(
    "/",
    requireRole(UserRole.PLATFORM_ADMIN),
    asyncHandler(async (req, res) => {
      const doc = await model.create(req.body);
      res.status(201).json(doc);
    })
  );

  router.put(
    "/:id",
    requireRole(UserRole.PLATFORM_ADMIN),
    asyncHandler(async (req, res) => {
      const doc = await model.findByIdAndUpdate(req.params.id, req.body, { new: true, runValidators: true });
      if (!doc) return res.status(404).json(apiError("NOT_FOUND", "Not found"));
      res.status(200).json(doc);
    })
  );

  router.delete(
    "/:id",
    requireRole(UserRole.PLATFORM_ADMIN),
    asyncHandler(async (req, res) => {
      if (options.inUse && (await options.inUse(String(req.params.id)))) {
        return res.status(409).json(apiError("IN_USE", "Still used by existing records, so it cannot be deleted"));
      }
      await model.findByIdAndDelete(req.params.id);
      res.status(204).send();
    })
  );

  return router;
}
