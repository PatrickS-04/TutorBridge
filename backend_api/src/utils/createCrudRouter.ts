import { Router } from "express";
import { Model } from "mongoose";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { paginate } from "../utils/paginate";
import { authenticate, requireRole } from "../middleware/auth";
import { UserRole } from "../models";

// Reference: Express.js. n.d. Routing. [Online].
//   Available at: https://expressjs.com/en/guide/routing/ [Accessed 21 September 2026].
export function createCrudRouter<T>(model: Model<T>) {
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
    requireRole(UserRole.INSTITUTION_ADMIN),
    asyncHandler(async (req, res) => {
      const doc = await model.create(req.body);
      res.status(201).json(doc);
    })
  );

  router.put(
    "/:id",
    requireRole(UserRole.INSTITUTION_ADMIN),
    asyncHandler(async (req, res) => {
      const doc = await model.findByIdAndUpdate(req.params.id, req.body, { new: true, runValidators: true });
      if (!doc) return res.status(404).json(apiError("NOT_FOUND", "Not found"));
      res.status(200).json(doc);
    })
  );

  router.delete(
    "/:id",
    requireRole(UserRole.INSTITUTION_ADMIN),
    asyncHandler(async (req, res) => {
      await model.findByIdAndDelete(req.params.id);
      res.status(204).send();
    })
  );

  return router;
}
