import { Router } from "express";
import { CalendarLink } from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { authenticate, AuthRequest } from "../middleware/auth";

const router = Router();
router.use(authenticate);

router.post(
  "/link",
  asyncHandler(async (req: AuthRequest, res) => {
    const { authCode, calendarId } = req.body;
    if (!authCode) return res.status(400).json(apiError("VALIDATION_ERROR", "authCode is required", "authCode"));

    const googleAccountEmail = `${req.auth!.userId}@placeholder.invalid`;
    const refreshTokenEnc = `enc:${authCode}`;

    const link = await CalendarLink.findOneAndUpdate(
      { userId: req.auth!.userId },
      {
        $set: {
          googleAccountEmail,
          calendarId: calendarId ?? "primary",
          refreshTokenEnc,
          syncEnabled: true,
        },
      },
      { new: true, upsert: true }
    );

    res.status(201).json({
      linked: true,
      googleAccountEmail: link.googleAccountEmail,
      syncEnabled: link.syncEnabled,
    });
  })
);

router.get(
  "/link",
  asyncHandler(async (req: AuthRequest, res) => {
    const link = await CalendarLink.findOne({ userId: req.auth!.userId });
    if (!link) return res.status(200).json({ linked: false });

    res.status(200).json({
      linked: true,
      googleAccountEmail: link.googleAccountEmail,
      syncEnabled: link.syncEnabled,
      lastSyncedAt: link.lastSyncedAt ?? null,
      lastSyncError: link.lastSyncError ?? null,
    });
  })
);

router.delete(
  "/link",
  asyncHandler(async (req: AuthRequest, res) => {
    await CalendarLink.deleteOne({ userId: req.auth!.userId });
    res.status(204).send();
  })
);

export default router;
