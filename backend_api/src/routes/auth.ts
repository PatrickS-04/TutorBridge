import { Router } from "express";
import admin from "firebase-admin";
import bcrypt from "bcryptjs";
import { IUser, User, UserRole, AuthProvider, VerificationStatus } from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { verifyFirebase, authenticate, AuthRequest } from "../middleware/auth";

const router = Router();

/**
 * Firebase owns sign-up, sign-in, Google SSO, password reset and token
 * refresh, so this file no longer issues or refreshes sessions of its own.
 * What is left is mapping a verified Firebase identity onto a TutorBridge
 * profile document, and retiring an admin-issued temporary password.
 */

function toProfile(user: IUser) {
  return {
    id: user._id,
    institutionId: user.institutionId,
    role: user.role,
    fullName: user.fullName,
    verificationStatus: user.verificationStatus,
    profileComplete: user.profileCompleteness,
    /** Still on an admin-issued temporary password; the app holds the tutor at
     * a new-password screen until POST /auth/change-password clears it. */
    mustChangePassword: user.mustChangePassword,
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

/**
 * Swaps the temporary password an admin provisioned a tutor with (see
 * institutions.ts) for one only the tutor knows. The server sets it rather
 * than the app, so mustChangePassword cannot be cleared without the password
 * really changing. Only while that flag is set: this changes a password on
 * the strength of a bearer token alone, so it must not become a way to change
 * anyone's.
 */
router.post(
  "/change-password",
  authenticate,
  asyncHandler(async (req: AuthRequest, res) => {
    const { newPassword } = req.body;
    // Firebase's own minimum - the same rule sign-up in the app applies.
    if (typeof newPassword !== "string" || newPassword.length < 6) {
      return res
        .status(400)
        .json(apiError("VALIDATION_ERROR", "newPassword must be at least 6 characters", "newPassword"));
    }

    const user = await User.findById(req.auth!.userId).select("+passwordHash");
    if (!user?.mustChangePassword) {
      return res.status(409).json(apiError("NOT_REQUIRED", "There is no temporary password to replace"));
    }
    if (user.passwordHash && (await bcrypt.compare(newPassword, user.passwordHash))) {
      return res
        .status(400)
        .json(apiError("VALIDATION_ERROR", "Choose a password other than the temporary one", "newPassword"));
    }

    // Reference: Firebase. 2026. Manage users. [Online].
    //   Available at: https://firebase.google.com/docs/auth/admin/manage-users [Accessed 21 September 2026].
    await admin.auth().updateUser(req.firebase!.uid, { password: newPassword });
    user.mustChangePassword = false;
    user.passwordHash = undefined;
    await user.save();
    res.status(204).send();
  })
);

export default router;
