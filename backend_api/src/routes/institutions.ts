import { Router } from "express";
import crypto from "crypto";
import bcrypt from "bcryptjs";
import admin from "firebase-admin";
import { Institution, User, VerificationDocument, UserRole, AuthProvider, VerificationStatus } from "../models";
import { asyncHandler, apiError } from "../utils/asyncHandler";
import { paginate } from "../utils/paginate";
import { authenticate, requireRole, AuthRequest } from "../middleware/auth";

const router = Router();
router.use(authenticate);

router.get(
  "/me",
  asyncHandler(async (req: AuthRequest, res) => {
    const institution = await Institution.findById(req.auth!.institutionId);
    if (!institution) return res.status(404).json(apiError("NOT_FOUND", "Institution not found"));
    res.status(200).json(institution);
  })
);

router.put(
  "/me",
  requireRole(UserRole.INSTITUTION_ADMIN),
  asyncHandler(async (req: AuthRequest, res) => {
    const { contactEmail, defaultLocale } = req.body;
    const institution = await Institution.findByIdAndUpdate(
      req.auth!.institutionId,
      { $set: { contactEmail, defaultLocale } },
      { new: true, runValidators: true }
    );
    if (!institution) return res.status(404).json(apiError("NOT_FOUND", "Institution not found"));
    res.status(200).json(institution);
  })
);

router.get(
  "/me/tutors",
  requireRole(UserRole.INSTITUTION_ADMIN),
  asyncHandler(async (req: AuthRequest, res) => {
    // DELETE below only stamps deactivatedAt, so a removed tutor is still in
    // the collection - leave them out here or "removed" means nothing.
    const result = await paginate(
      User,
      { institutionId: req.auth!.institutionId, role: UserRole.TUTOR, deactivatedAt: null },
      req,
      "subjectIds"
    );
    res.status(200).json(result);
  })
);

router.post(
  "/me/tutors",
  requireRole(UserRole.INSTITUTION_ADMIN),
  asyncHandler(async (req: AuthRequest, res) => {
    const { fullName, email, subjectIds, department, staffNumber, hourlyRate } = req.body;
    if (!fullName || !email) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "fullName and email are required"));
    }

    // References:
    // Node.js. 2026. Crypto (Node.js v22.23.2 documentation). [Online].
    //   Available at: https://nodejs.org/docs/latest-v22.x/api/crypto.html [Accessed 21 September 2026].
    // Wirtz, D. 2025. bcrypt.js (Version 3.0.3). [Computer program].
    //   Available at: https://github.com/dcodeIO/bcrypt.js [Accessed 21 September 2026].
    // Always 10 characters: base64url has no "+", "/" or padding to strip, and
    // Firebase refuses a password under 6.
    const temporaryPassword = crypto.randomBytes(8).toString("base64url").slice(0, 10);

    const tutor = new User({
      institutionId: req.auth!.institutionId,
      role: UserRole.TUTOR,
      fullName,
      email,
      // Firebase holds the password itself; this hash only lets
      // POST /auth/change-password refuse the temporary one back.
      passwordHash: await bcrypt.hash(temporaryPassword, 12),
      authProvider: AuthProvider.PASSWORD,
      subjectIds: subjectIds ?? [],
      department,
      staffNumber,
      hourlyRate,
      verificationStatus: VerificationStatus.VERIFIED,
      mustChangePassword: true,
      createdBy: req.auth!.userId,
    });

    // Everything that can turn the request away runs before a Firebase login
    // exists, so a rejected request never leaves one behind.
    await tutor.validate();
    if (await User.exists({ email: tutor.email })) {
      return res.status(409).json(apiError("EMAIL_TAKEN", "Email already registered", "email"));
    }

    // The app signs in against Firebase, so that is where the temporary
    // password has to work - a profile on its own is a login nobody can use.
    // Reference: Firebase. 2026. Manage users. [Online].
    //   Available at: https://firebase.google.com/docs/auth/admin/manage-users [Accessed 21 September 2026].
    let firebaseUser;
    try {
      firebaseUser = await admin.auth().createUser({
        email: tutor.email,
        password: temporaryPassword,
        displayName: tutor.fullName,
        emailVerified: true, // the institution vouches for its staff address, as in seedTutors.ts
      });
    } catch (err: any) {
      if (err?.code === "auth/email-already-exists") {
        return res.status(409).json(apiError("EMAIL_TAKEN", "Email already registered", "email"));
      }
      if (err?.code === "auth/invalid-email") {
        return res.status(400).json(apiError("VALIDATION_ERROR", "email is not a valid address", "email"));
      }
      throw err;
    }

    tutor.firebaseUid = firebaseUser.uid;
    try {
      await tutor.save();
    } catch (err) {
      // A login with no profile behind it can only ever fail - take it back out.
      await admin.auth().deleteUser(firebaseUser.uid);
      throw err;
    }

    res.status(201).json({
      id: tutor._id,
      role: tutor.role,
      verificationStatus: tutor.verificationStatus,
      temporaryPassword,
      mustChangePassword: true,
    });
  })
);

router.delete(
  "/me/tutors/:id",
  requireRole(UserRole.INSTITUTION_ADMIN),
  asyncHandler(async (req: AuthRequest, res) => {
    const tutor = await User.findOneAndUpdate(
      { _id: req.params.id, institutionId: req.auth!.institutionId, role: UserRole.TUTOR },
      { $set: { deactivatedAt: new Date() } }
    );
    if (!tutor) return res.status(404).json(apiError("NOT_FOUND", "Tutor not found"));
    res.status(204).send();
  })
);

router.get(
  "/me/verification-queue",
  requireRole(UserRole.INSTITUTION_ADMIN),
  asyncHandler(async (req: AuthRequest, res) => {
    const result = await paginate(
      VerificationDocument,
      { institutionId: req.auth!.institutionId, status: VerificationStatus.PENDING },
      req
    );
    res.status(200).json(result);
  })
);

router.put(
  "/verification/:userId",
  requireRole(UserRole.INSTITUTION_ADMIN),
  asyncHandler(async (req: AuthRequest, res) => {
    const { decision } = req.body;
    if (!["accept", "reject"].includes(decision)) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "decision must be 'accept' or 'reject'", "decision"));
    }

    const newStatus = decision === "accept" ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED;

    const user = await User.findOneAndUpdate(
      { _id: req.params.userId, institutionId: req.auth!.institutionId },
      { $set: { verificationStatus: newStatus } },
      { new: true }
    );
    if (!user) return res.status(404).json(apiError("NOT_FOUND", "User not found"));

    await VerificationDocument.updateMany(
      { userId: user._id, status: VerificationStatus.PENDING },
      { $set: { status: newStatus, reviewedAt: new Date(), reviewedBy: req.auth!.userId } }
    );

    res.status(200).json({ userId: user._id, status: user.verificationStatus, reviewedAt: new Date() });
  })
);

export default router;
