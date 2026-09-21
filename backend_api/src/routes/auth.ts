import { Router } from "express";
import { IUser, User, UserRole, AuthProvider, VerificationStatus } from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { verifyFirebase, AuthRequest } from "../middleware/auth";

const router = Router();

/**
 * Firebase owns sign-up, sign-in, Google SSO, password reset and token
 * refresh, so this file no longer issues or refreshes sessions of its own.
 * What is left is mapping a verified Firebase identity onto a TutorBridge
 * profile document.
 */

function toProfile(user: IUser) {
  return {
    id: user._id,
    institutionId: user.institutionId,
    role: user.role,
    fullName: user.fullName,
    verificationStatus: user.verificationStatus,
    profileComplete: user.profileCompleteness,
  };
}

/**
 * Create-or-return the profile for the signed-in Firebase user. The app calls
 * this straight after every Firebase sign-in; it is idempotent.
 *
 * [institutionId] is only needed the first time a brand-new account signs in.
 */
router.post(
  "/bootstrap",
  verifyFirebase,
  asyncHandler(async (req: AuthRequest, res) => {
    const { uid, email, name, signInProvider } = req.firebase!;
    if (!email) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "Firebase account has no email address", "email"));
    }

    const byUid = await User.findOne({ firebaseUid: uid });
    if (byUid) return res.status(200).json(toProfile(byUid));

    // An institution admin may have pre-created this person (see
    // institutions.ts) before they ever signed in. Link that profile to the
    // Firebase identity rather than creating a duplicate.
    const byEmail = await User.findOne({ email: email.toLowerCase() });
    if (byEmail) {
      byEmail.firebaseUid = uid;
      await byEmail.save();
      return res.status(200).json(toProfile(byEmail));
    }

    const { institutionId, fullName } = req.body;
    if (!institutionId) {
      return res
        .status(400)
        .json(apiError("VALIDATION_ERROR", "institutionId is required to create a new profile", "institutionId"));
    }

    const user = await User.create({
      institutionId,
      role: UserRole.GUARDIAN_LEARNER,
      fullName: fullName || name || email.split("@")[0],
      email: email.toLowerCase(),
      firebaseUid: uid,
      authProvider: signInProvider === "google.com" ? AuthProvider.GOOGLE : AuthProvider.PASSWORD,
      verificationStatus: VerificationStatus.UNVERIFIED,
      profileCompleteness: 20,
    });

    res.status(201).json(toProfile(user));
  })
);

export default router;
