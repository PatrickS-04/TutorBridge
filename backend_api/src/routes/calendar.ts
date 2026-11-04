import { Router } from "express";
import { CalendarLink } from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { authenticate, AuthRequest } from "../middleware/auth";
import { exchangeAuthCode, encryptToken, syncUpcomingBookings, CALENDAR_SCOPE } from "../utils/googleCalendar";

const router = Router();
router.use(authenticate);

router.post(
  "/link",
  asyncHandler(async (req: AuthRequest, res) => {
    const { authCode, calendarId } = req.body;
    if (!authCode) return res.status(400).json(apiError("VALIDATION_ERROR", "authCode is required", "authCode"));

    //The code is one-time and short-lived, so a bad one is the user's problem
    //to retry, not a 500.
    let googleAccountEmail: string;
    let refreshTokenEnc: string;
    try {
      const granted = await exchangeAuthCode(authCode);
      //Labelling the link is not worth failing it: if Google told us nothing,
      //the account they signed in with is the best guess we have.
      googleAccountEmail = granted.email || req.firebase?.email || "Google account";
      refreshTokenEnc = encryptToken(granted.refreshToken);
    } catch (error) {
      console.error("calendar link failed:", error);
      return res
        .status(400)
        .json(apiError("GOOGLE_AUTH_FAILED", "Could not link that Google account - please try again", "authCode"));
    }

    const link = await CalendarLink.findOneAndUpdate(
      { userId: req.auth!.userId },
      {
        $set: {
          googleAccountEmail,
          calendarId: calendarId ?? "primary",
          refreshTokenEnc,
          scopes: [CALENDAR_SCOPE],
          syncEnabled: true,
        },
      },
      { new: true, upsert: true }
    );

    //Not awaited: the link has worked, however long Google takes over the backlog.
    syncUpcomingBookings(req.auth!.userId).catch((error) => console.error("calendar catch-up failed:", error));

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
