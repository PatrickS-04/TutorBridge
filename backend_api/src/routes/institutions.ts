import { Router } from "express";
import crypto from "crypto";
import bcrypt from "bcryptjs";
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
    const result = await paginate(
      User,
      { institutionId: req.auth!.institutionId, role: UserRole.TUTOR },
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
    const { fullName, email, subjectIds } = req.body;
    if (!fullName || !email) {
      return res.status(400).json(apiError("VALIDATION_ERROR", "fullName and email are required"));
    }

    const existing = await User.findOne({ email: String(email).toLowerCase() });
    if (existing) return res.status(409).json(apiError("EMAIL_TAKEN", "Email already registered", "email"));

    const temporaryPassword = crypto.randomBytes(6).toString("base64").replace(/[+/=]/g, "").slice(0, 10);
    const passwordHash = await bcrypt.hash(temporaryPassword, 12);

    const tutor = await User.create({
      institutionId: req.auth!.institutionId,
      role: UserRole.TUTOR,
      fullName,
      email: String(email).toLowerCase(),
      passwordHash,
      authProvider: AuthProvider.PASSWORD,
      subjectIds: subjectIds ?? [],
      verificationStatus: VerificationStatus.VERIFIED,
      mustChangePassword: true,
      createdBy: req.auth!.userId,
    });

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
      { $set: { deletionScheduledFor: new Date() } }
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
