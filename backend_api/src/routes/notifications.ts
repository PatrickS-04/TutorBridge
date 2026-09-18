import { Router } from "express";
import { NotificationEvent } from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { paginate } from "../utils/paginate";
import { authenticate, AuthRequest } from "../middleware/auth";

const router = Router();
router.use(authenticate);

/** This user's notifications, newest first - the notifications screen. */
router.get(
  "/",
  asyncHandler(async (req: AuthRequest, res) => {
    const result = await paginate(NotificationEvent, { userId: req.auth!.userId }, req);
    res.status(200).json(result);
  })
);

router.put(
  "/:id/read",
  asyncHandler(async (req: AuthRequest, res) => {
    const event = await NotificationEvent.findOneAndUpdate(
      { _id: req.params.id, userId: req.auth!.userId, readAt: null },
      { $set: { readAt: new Date() } },
      { new: true }
    );
    if (!event) return res.status(404).json(apiError("NOT_FOUND", "Notification not found"));
    res.status(200).json({ id: event._id, readAt: event.readAt });
  })
);

export default router;
