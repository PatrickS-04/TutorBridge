import { Router } from "express";
import { NotificationToken } from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { authenticate, AuthRequest } from "../middleware/auth";

const router = Router();
router.use(authenticate);

router.post(
  "/",
  asyncHandler(async (req: AuthRequest, res) => {
    const { token, platform } = req.body;
    if (!token) return res.status(400).json(apiError("VALIDATION_ERROR", "token is required", "token"));

    await NotificationToken.findOneAndUpdate(
      { token },
      { $set: { userId: req.auth!.userId, platform: platform ?? "android", lastSeenAt: new Date() } },
      { upsert: true }
    );

    res.status(200).json({ registered: true });
  })
);

router.delete(
  "/:token",
  asyncHandler(async (req: AuthRequest, res) => {
    await NotificationToken.deleteOne({ token: req.params.token, userId: req.auth!.userId });
    res.status(204).send();
  })
);

export default router;
